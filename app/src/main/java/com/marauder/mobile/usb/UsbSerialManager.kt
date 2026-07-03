package com.marauder.mobile.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Owns the USB-OTG serial link to the ESP32 Marauder. Exposes incoming lines and
 * connection state as flows; commands are written on a background dispatcher.
 */
class UsbSerialManager(context: Context) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class Status { DISCONNECTED, CONNECTING, CONNECTED }

    /** A connectable serial adapter discovered on the USB bus. */
    class DeviceOption(val driver: UsbSerialDriver) {
        val device: UsbDevice get() = driver.device
        val id: Int get() = device.deviceId
        val title: String get() = deviceLabel(device)
        val subtitle: String get() = String.format("VID %04X · PID %04X", device.vendorId, device.productId)
    }

    private val _status = MutableStateFlow(Status.DISCONNECTED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 2048)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val lineBuf = ByteArrayOutputStream(512)
    private var pendingDriver: UsbSerialDriver? = null
    private var receiverRegistered = false

    companion object {
        const val BAUD = 115200
        private const val WRITE_TIMEOUT_MS = 1000
        private const val ACTION_USB_PERMISSION = "com.marauder.mobile.USB_PERMISSION"

        fun deviceLabel(device: UsbDevice): String =
            device.productName?.takeIf { it.isNotBlank() }
                ?: String.format("Serial %04X:%04X", device.vendorId, device.productId)
    }

    /** Default drivers + Espressif native USB-CDC (ESP32-S2/S3/C3) that the
     *  stock probe table does not cover. */
    private val prober: UsbSerialProber = run {
        val table: ProbeTable = UsbSerialProber.getDefaultProbeTable()
        table.addProduct(0x303A, 0x0002, CdcAcmSerialDriver::class.java) // ESP32-S2 CDC
        table.addProduct(0x303A, 0x1001, CdcAcmSerialDriver::class.java) // ESP32-S3/C3 USB-Serial-JTAG
        table.addProduct(0x303A, 0x4001, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x0009, CdcAcmSerialDriver::class.java)
        UsbSerialProber(table)
    }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val driver = pendingDriver
            pendingDriver = null
            if (granted && driver != null) {
                openDriver(driver)
            } else {
                _status.value = Status.DISCONNECTED
                emitEvent("USB permission denied")
            }
        }
    }

    private fun ensureReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        // App-private receiver: our own USB-permission broadcast is package-scoped,
        // so mark it not-exported. ContextCompat applies the flag correctly on every
        // API level (a no-op below Android 13, where the flag does not exist).
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun availableDevices(): List<DeviceOption> =
        prober.findAllDrivers(usbManager).map { DeviceOption(it) }

    /** Connect to the first adapter found; used by USB auto-attach. */
    fun connectFirst(): Boolean {
        val first = availableDevices().firstOrNull() ?: run {
            emitEvent("No USB serial adapter found")
            return false
        }
        connect(first)
        return true
    }

    fun connect(option: DeviceOption) {
        if (_status.value != Status.DISCONNECTED) disconnect()
        ensureReceiver()
        _status.value = Status.CONNECTING
        val driver = option.driver
        if (usbManager.hasPermission(driver.device)) {
            openDriver(driver)
        } else {
            pendingDriver = driver
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
            val pi = PendingIntent.getBroadcast(appContext, 0, intent, flags)
            usbManager.requestPermission(driver.device, pi)
        }
    }

    private fun openDriver(driver: UsbSerialDriver) {
        scope.launch {
            try {
                val connection = usbManager.openDevice(driver.device)
                    ?: throw IllegalStateException("openDevice() returned null")
                val p = driver.ports.first()
                p.open(connection)
                p.setParameters(BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                // Assert both lines together: the ESP32 auto-reset circuit only
                // resets on a DTR/RTS mismatch, and native USB-CDC needs DTR set.
                runCatching { p.setDTR(true) }
                runCatching { p.setRTS(true) }

                synchronized(lineBuf) { lineBuf.reset() }
                val io = SerialInputOutputManager(p, listener)
                io.start()

                port = p
                ioManager = io
                _connectedName.value = deviceLabel(driver.device)
                _status.value = Status.CONNECTED
                emitEvent("Connected to ${deviceLabel(driver.device)}")
            } catch (e: Exception) {
                cleanup()
                _status.value = Status.DISCONNECTED
                emitEvent("Connect failed: ${e.message}")
            }
        }
    }

    private val listener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            synchronized(lineBuf) {
                for (b in data) {
                    when (b.toInt()) {
                        '\n'.code -> {
                            val line = lineBuf.toString("UTF-8")
                            lineBuf.reset()
                            if (line.isNotEmpty()) _lines.tryEmit(line)
                        }
                        '\r'.code -> { /* drop CR */ }
                        else -> lineBuf.write(b.toInt())
                    }
                }
            }
        }

        override fun onRunError(e: Exception) {
            emitEvent("Serial error: ${e.message}")
            disconnect()
        }
    }

    /** Queue a command (a newline is appended). Written on the IO dispatcher. */
    fun send(command: String) {
        val text = command.trim()
        if (text.isEmpty()) return
        scope.launch {
            val p = port ?: run { emitEvent("Not connected"); return@launch }
            try {
                p.write((text + "\n").toByteArray(Charsets.UTF_8), WRITE_TIMEOUT_MS)
            } catch (e: Exception) {
                emitEvent("Write failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        cleanup()
        if (_status.value != Status.DISCONNECTED) emitEvent("Disconnected")
        _status.value = Status.DISCONNECTED
        _connectedName.value = null
    }

    private fun cleanup() {
        runCatching { ioManager?.stop() }
        ioManager = null
        runCatching { port?.close() }
        port = null
    }

    private fun emitEvent(msg: String) {
        _events.tryEmit(msg)
    }
}
