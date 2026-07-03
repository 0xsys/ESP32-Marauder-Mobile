package com.marauder.mobile.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marauder.mobile.MarauderApp
import com.marauder.mobile.data.AnalyzerKind
import com.marauder.mobile.data.ListType
import com.marauder.mobile.protocol.DeviceMessage
import com.marauder.mobile.protocol.LineParser
import com.marauder.mobile.protocol.ParsedLine
import com.marauder.mobile.usb.UsbSerialManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LineKind { INPUT, OUTPUT, SYSTEM, ERROR }

data class ConsoleEntry(val id: Long, val text: String, val kind: LineKind)

data class AnalyzerState(
    val kind: AnalyzerKind? = null,
    val samples: List<Int> = emptyList(),
    val channels: List<Int> = emptyList(),
    val values: List<Int> = emptyList(),
    val page: Int = 0,
)

class MarauderViewModel(application: Application) : AndroidViewModel(application) {

    private val usb: UsbSerialManager = (application as MarauderApp).usb
    private val settings = (application as MarauderApp).settings

    // --- Connection ----------------------------------------------------------
    val status: StateFlow<UsbSerialManager.Status> = usb.status
    val connectedName: StateFlow<String?> = usb.connectedName

    // --- Theme (persisted; dark is the default) ------------------------------
    val darkTheme: StateFlow<Boolean> =
        settings.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun toggleTheme() {
        viewModelScope.launch { settings.setDarkTheme(!darkTheme.value) }
    }

    // --- Device state --------------------------------------------------------
    private val _info = MutableStateFlow<DeviceMessage.Info?>(null)
    val info: StateFlow<DeviceMessage.Info?> = _info.asStateFlow()

    private val _deviceStatus = MutableStateFlow<DeviceMessage.Status?>(null)
    val deviceStatus: StateFlow<DeviceMessage.Status?> = _deviceStatus.asStateFlow()

    // --- Structured lists ----------------------------------------------------
    private val _aps = MutableStateFlow<List<DeviceMessage.Ap>>(emptyList())
    val aps: StateFlow<List<DeviceMessage.Ap>> = _aps.asStateFlow()
    private val _stations = MutableStateFlow<List<DeviceMessage.Sta>>(emptyList())
    val stations: StateFlow<List<DeviceMessage.Sta>> = _stations.asStateFlow()
    private val _ssids = MutableStateFlow<List<DeviceMessage.SsidRow>>(emptyList())
    val ssids: StateFlow<List<DeviceMessage.SsidRow>> = _ssids.asStateFlow()
    private val _ips = MutableStateFlow<List<DeviceMessage.Ip>>(emptyList())
    val ips: StateFlow<List<DeviceMessage.Ip>> = _ips.asStateFlow()
    private val _probes = MutableStateFlow<List<DeviceMessage.Probe>>(emptyList())
    val probes: StateFlow<List<DeviceMessage.Probe>> = _probes.asStateFlow()
    private val _airtags = MutableStateFlow<List<DeviceMessage.Airtag>>(emptyList())
    val airtags: StateFlow<List<DeviceMessage.Airtag>> = _airtags.asStateFlow()

    private val _listLoading = MutableStateFlow<ListType?>(null)
    val listLoading: StateFlow<ListType?> = _listLoading.asStateFlow()

    // --- Console -------------------------------------------------------------
    private val _console = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    val console: StateFlow<List<ConsoleEntry>> = _console.asStateFlow()

    // --- Analyzer ------------------------------------------------------------
    private val _analyzer = MutableStateFlow(AnalyzerState())
    val analyzer: StateFlow<AnalyzerState> = _analyzer.asStateFlow()

    // --- One-shot messages ---------------------------------------------------
    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val apBuf = ArrayList<DeviceMessage.Ap>()
    private val staBuf = ArrayList<DeviceMessage.Sta>()
    private val ssidBuf = ArrayList<DeviceMessage.SsidRow>()
    private val ipBuf = ArrayList<DeviceMessage.Ip>()
    private val probeBuf = ArrayList<DeviceMessage.Probe>()
    private val airtagBuf = ArrayList<DeviceMessage.Airtag>()

    private var consoleSeq = 0L
    private var sessionJob: Job? = null
    private var liveJob: Job? = null

    init {
        viewModelScope.launch { usb.lines.collect(::onLine) }
        viewModelScope.launch {
            usb.events.collect { msg ->
                appendConsole(msg, LineKind.SYSTEM)
                _snackbar.tryEmit(msg)
            }
        }
        viewModelScope.launch {
            usb.status.collect { st ->
                when (st) {
                    UsbSerialManager.Status.CONNECTED -> startSession()
                    UsbSerialManager.Status.DISCONNECTED -> {
                        sessionJob?.cancel()
                        onDisconnected()
                    }
                    else -> {}
                }
            }
        }
    }

    // --- Connection API ------------------------------------------------------
    fun availableDevices(): List<UsbSerialManager.DeviceOption> = usb.availableDevices()
    fun connect(option: UsbSerialManager.DeviceOption) = usb.connect(option)
    fun connectFirst(): Boolean = usb.connectFirst()
    fun disconnect() = usb.disconnect()

    // --- Command API ---------------------------------------------------------

    /** Run a firmware command and echo it into the console. */
    fun runCommand(command: String) {
        val text = command.trim()
        if (text.isEmpty()) return
        appendConsole("> $text", LineKind.INPUT)
        usb.send(text)
    }

    fun stopScan() = runCommand("stopscan")

    fun refreshList(type: ListType) {
        bufferFor(type).clear()
        _listLoading.value = type
        usb.send("jsonlist ${type.code}") // internal: not echoed
    }

    fun startAnalyzer(command: String, kind: AnalyzerKind) {
        _analyzer.value = AnalyzerState(kind = kind)
        appendConsole("> $command", LineKind.INPUT)
        usb.send(command)
    }

    /** The id the next console entry will get — capture before [startLiveActivity]
     *  so a live screen can show only the output produced by its own command. */
    fun consoleMark(): Long = consoleSeq

    /** Run [command] and keep its live view fed: poll status (and its list, if any)
     *  every ~1.3 s while it runs so counters and rows update in real time. */
    fun startLiveActivity(command: String, list: ListType?) {
        runCommand(command)
        list?.let {
            bufferFor(it).clear()
            _listLoading.value = it
        }
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            delay(500)
            while (isActive && usb.status.value == UsbSerialManager.Status.CONNECTED) {
                usb.send("jsonstatus")
                if (list != null) usb.send("jsonlist ${list.code}")
                delay(1300)
            }
        }
    }

    /** Leave a live screen: stop polling and, for continuous scans/attacks, stop the
     *  firmware scan (mirrors pressing Back on the device). */
    fun stopLiveActivity(continuous: Boolean) {
        liveJob?.cancel()
        liveJob = null
        _listLoading.value = null
        if (continuous) usb.send("stopscan")
    }

    fun clearConsole() {
        _console.value = emptyList()
    }

    // --- Incoming ------------------------------------------------------------
    private fun onLine(line: String) {
        when (val parsed = LineParser.parse(line)) {
            is ParsedLine.Console -> if (parsed.raw.isNotBlank()) appendConsole(parsed.raw, LineKind.OUTPUT)
            is ParsedLine.Malformed -> appendConsole(parsed.raw, LineKind.OUTPUT)
            is ParsedLine.Structured -> handleMessage(parsed.message)
        }
    }

    private fun handleMessage(msg: DeviceMessage) {
        when (msg) {
            is DeviceMessage.Info -> {
                _info.value = msg
                appendConsole(
                    "◈ ${msg.board} · fw ${msg.fw} · proto ${msg.proto} · [${msg.caps.joinToString(", ")}]",
                    LineKind.SYSTEM,
                )
            }
            is DeviceMessage.Status -> _deviceStatus.value = msg
            is DeviceMessage.JsonMode ->
                appendConsole("JSON mode ${if (msg.on) "enabled" else "disabled"}", LineKind.SYSTEM)
            is DeviceMessage.Ap -> apBuf.add(msg)
            is DeviceMessage.Sta -> staBuf.add(msg)
            is DeviceMessage.SsidRow -> ssidBuf.add(msg)
            is DeviceMessage.Ip -> ipBuf.add(msg)
            is DeviceMessage.Probe -> probeBuf.add(msg)
            is DeviceMessage.Airtag -> airtagBuf.add(msg)
            is DeviceMessage.End -> commitList(msg.list)
            is DeviceMessage.Err -> {
                appendConsole("✗ ${msg.cmd} ${msg.arg}".trim(), LineKind.ERROR)
                _snackbar.tryEmit("Device error: ${msg.cmd}")
            }
            is DeviceMessage.AnalyzerSample -> onSample(msg)
            is DeviceMessage.ChannelActivity -> onChannelActivity(msg)
            is DeviceMessage.Unknown -> {}
        }
    }

    private fun commitList(listCode: String) {
        when (listCode) {
            "a" -> { _aps.value = apBuf.toList(); apBuf.clear() }
            "s" -> { _ssids.value = ssidBuf.toList(); ssidBuf.clear() }
            "c" -> { _stations.value = staBuf.toList(); staBuf.clear() }
            "i" -> { _ips.value = ipBuf.toList(); ipBuf.clear() }
            "p" -> { _probes.value = probeBuf.toList(); probeBuf.clear() }
            "t" -> { _airtags.value = airtagBuf.toList(); airtagBuf.clear() }
        }
        _listLoading.value = null
    }

    private fun onSample(s: DeviceMessage.AnalyzerSample) {
        val cur = _analyzer.value
        val next = (cur.samples + s.value).takeLast(MAX_SAMPLES)
        _analyzer.value = cur.copy(samples = next)
    }

    private fun onChannelActivity(c: DeviceMessage.ChannelActivity) {
        _analyzer.value = _analyzer.value.copy(
            channels = c.channels,
            values = c.values,
            page = c.page,
        )
    }

    // --- Session lifecycle ---------------------------------------------------
    private fun startSession() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            // A fresh connection may pulse the ESP32 auto-reset line; give it time
            // to finish booting, then handshake (twice, in case the first is lost).
            delay(350)
            usb.send("jsoninfo")
            delay(1300)
            if (!isActive) return@launch
            usb.send("jsoninfo")
            delay(200)
            usb.send("jsonstatus")
            while (isActive && usb.status.value == UsbSerialManager.Status.CONNECTED) {
                delay(2500)
                usb.send("jsonstatus")
            }
        }
    }

    private fun onDisconnected() {
        _info.value = null
        _deviceStatus.value = null
        _aps.value = emptyList()
        _stations.value = emptyList()
        _ssids.value = emptyList()
        _ips.value = emptyList()
        _probes.value = emptyList()
        _airtags.value = emptyList()
        _analyzer.value = AnalyzerState()
        _listLoading.value = null
    }

    // --- Helpers -------------------------------------------------------------
    private fun bufferFor(type: ListType): ArrayList<*> = when (type) {
        ListType.ACCESS_POINTS -> apBuf
        ListType.SSIDS -> ssidBuf
        ListType.STATIONS -> staBuf
        ListType.IPS -> ipBuf
        ListType.PROBES -> probeBuf
        ListType.AIRTAGS -> airtagBuf
    }

    private fun appendConsole(text: String, kind: LineKind) {
        val entry = ConsoleEntry(consoleSeq++, text, kind)
        _console.value = (_console.value + entry).takeLast(MAX_CONSOLE)
    }

    companion object {
        private const val MAX_CONSOLE = 800
        private const val MAX_SAMPLES = 120
    }
}
