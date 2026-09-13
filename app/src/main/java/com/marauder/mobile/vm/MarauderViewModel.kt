package com.marauder.mobile.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marauder.mobile.MarauderApp
import com.marauder.mobile.capture.CaptureSink
import com.marauder.mobile.data.AnalyzerKind
import com.marauder.mobile.data.GpxAssembler
import com.marauder.mobile.data.ListType
import com.marauder.mobile.data.LiveClassifier
import com.marauder.mobile.data.NmeaAssembler
import com.marauder.mobile.data.WardriveAssembler
import com.marauder.mobile.location.LocationRepository
import kotlinx.coroutines.flow.collectLatest
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import com.marauder.mobile.protocol.CaptureFrame
import com.marauder.mobile.protocol.Crc32
import com.marauder.mobile.protocol.DeviceMessage
import com.marauder.mobile.protocol.LineParser
import com.marauder.mobile.protocol.ParsedLine
import com.marauder.mobile.esp.EspFlasher
import com.marauder.mobile.esp.Firmware
import com.marauder.mobile.esp.FirmwareRepository
import com.marauder.mobile.esp.FlashProfile
import com.marauder.mobile.esp.FlashStage
import com.marauder.mobile.esp.FlashUiState
import com.marauder.mobile.esp.UsbFlashLink
import com.marauder.mobile.usb.UsbSerialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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

/** Live state of an on-phone capture (pcap streamed to storage, or a wardrive CSV). */
data class CaptureUiState(
    val active: Boolean = false,
    val path: String? = null,
    val kind: String = "",          // "pcap" | "wardrive"
    val bytes: Long = 0,
    val missedFrames: Long = 0,     // gaps in the frame sequence (transport loss)
    val droppedPackets: Long = 0,   // packets the device ring couldn't hold
    val rows: Int = 0,              // wardrive rows written
)

/** Evil Portal with a host-supplied page: pick an HTML file on the phone, stream
 *  it to the device (no SD card), target a scanned AP, then run and watch creds. */
data class EvilPortalUiState(
    val phase: Phase = Phase.NeedsHtml,
    val fileName: String? = null,
    val htmlBytes: Int = 0,
    val deviceMax: Int = 0,         // MAX_HTML_SIZE reported by the device (0 until known)
    val targetApIndex: Int? = null,
    val message: String? = null,
    val creds: List<DeviceMessage.Cred> = emptyList(),
) {
    enum class Phase { NeedsHtml, Uploading, Ready, Running, Error }
    val htmlReady: Boolean get() = phase == Phase.Ready || phase == Phase.Running
}

class MarauderViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MarauderApp
    private val usb: UsbSerialManager = app.usb
    private val settings = app.settings
    private val captureSink: CaptureSink = app.captureSink
    private val location: LocationRepository = app.location

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

    // --- Firmware flashing ---------------------------------------------------
    private val _flash = MutableStateFlow(FlashUiState())
    val flash: StateFlow<FlashUiState> = _flash.asStateFlow()

    // --- On-phone capture (SD-card / GPS-module replacement) -----------------
    private val _capture = MutableStateFlow(CaptureUiState())
    val capture: StateFlow<CaptureUiState> = _capture.asStateFlow()

    // --- Evil Portal with a host-supplied page (SD-card replacement) ---------
    private val _evilPortal = MutableStateFlow(EvilPortalUiState())
    val evilPortal: StateFlow<EvilPortalUiState> = _evilPortal.asStateFlow()

    // Upload handshake replies ({"t":"portal",...}); the upload coroutine awaits
    // "recv" then "set". extraBufferCapacity so tryEmit never drops a reply.
    private val portalEvents = MutableSharedFlow<DeviceMessage.Portal>(extraBufferCapacity = 8)
    private var portalJob: Job? = null

    /** True once the connected firmware advertised the proto ≥ 2 "capstream" cap. */
    private var captureCapable = false
    private var negotiated = false
    private var wardriving = false
    private val wardriveSeen = HashSet<String>()

    // Phone-GPS activities (each runs while its live screen is open; the SD-less
    // board has no GPS module, so these are served from the phone's own GNSS).
    private var gpsData = false      // gpsdata / nmea live console
    private var gpsTracking = false  // gpstracker → GPX track file
    private var poiOpen = false      // gpspoi session (persists across screens until -e)
    private var poiOut: OutputStream? = null
    private var poiPath: String? = null
    private var poiCount = 0

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
    private var flashJob: Job? = null

    init {
        viewModelScope.launch { usb.lines.collect(::onLine) }
        viewModelScope.launch {
            // Binary capture frames carved out of the serial stream: append each to
            // the active capture file and keep the UI counters current.
            usb.captureFrames.collect { frame ->
                if (captureSink.isActive) {
                    captureSink.write(frame)
                    _capture.value = _capture.value.copy(
                        bytes = captureSink.bytes,
                        missedFrames = captureSink.missedFrames,
                    )
                }
            }
        }
        viewModelScope.launch {
            usb.events.collect { msg ->
                appendConsole(msg, LineKind.SYSTEM)
                _snackbar.tryEmit(msg)
            }
        }
        viewModelScope.launch {
            // While wardriving, turn each refreshed AP list into WigleWiFi rows.
            aps.collect { if (wardriving) onWardriveAps(it) }
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
     *  every ~1.3 s while it runs so counters and rows update in real time.
     *
     *  When the firmware supports capstream (proto ≥ 2), a pcap-producing command
     *  is streamed to phone storage (`-serial`) instead of the device SD card, and
     *  `wardrive` is served entirely on the phone (AP list + phone GPS → CSV). */
    fun startLiveActivity(command: String, list: ListType?) {
        val trimmed = command.trim()
        val head = trimmed.substringBefore(' ')

        if (head == "wardrive" && captureCapable) {
            startWardrive(trimmed)
            return
        }
        // GPS features are served from the phone's GNSS (the board has no module).
        when (head) {
            "gpsdata" -> { startGpsConsole(nmea = false); return }
            "nmea" -> { startGpsConsole(nmea = true); return }
            "gpstracker" -> { if (trimmed.contains("stop")) stopTracking() else startTracking(); return }
            "gpspoi" -> { handlePoi(trimmed); return }
        }

        val capturing = LiveClassifier.of(trimmed).capture && captureCapable
        if (capturing) {
            val path = captureSink.begin(trimmed.substringBefore(' '), CaptureFrame.TYPE_PCAP)
            _capture.value = CaptureUiState(active = true, path = path, kind = "pcap")
        }

        // Append -serial so the firmware streams the pcap to us over the wire.
        appendConsole("> $trimmed", LineKind.INPUT)
        usb.send(if (capturing) "$trimmed -serial" else trimmed)

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

    /** Leave a live screen: stop polling, close any capture, and for continuous
     *  scans/attacks stop the firmware scan (mirrors pressing Back on the device). */
    fun stopLiveActivity(continuous: Boolean) {
        liveJob?.cancel()
        liveJob = null
        _listLoading.value = null
        if (wardriving) stopWardrive()
        if (gpsTracking) { stopTracking(); return } // stopTracking closes the GPX + capture itself
        if (gpsData) { gpsData = false; maybeStopLocation() }
        if (captureSink.isActive) endCapture()
        // A GPS-only screen (gpsdata/nmea/tracker/poi) never started a device scan,
        // so only send stopscan for real device scans/attacks.
        if (continuous && !gpsData) usb.send("stopscan")
    }

    // --- Location permission (requested from the live screen) ----------------

    fun hasLocationPermission(): Boolean = location.hasPermission()

    /** Called back after the runtime permission prompt: (re)start the GNSS for
     *  whatever phone-GPS activity is currently running. */
    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            if (wardriving || gpsData || gpsTracking || poiOpen) location.start()
        } else {
            _snackbar.tryEmit("Location permission denied — GPS features need it")
        }
    }

    // --- Phone GPS: data / NMEA / tracker / POI ------------------------------

    private fun startGpsConsole(nmea: Boolean) {
        gpsData = true
        val ok = location.start()
        appendConsole("> ${if (nmea) "nmea" else "gpsdata"} (phone GPS)", LineKind.INPUT)
        if (!ok) appendConsole("Grant the location permission and turn GPS on to see fixes.", LineKind.SYSTEM)
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            while (isActive) {
                val l = location.location.value
                when {
                    l == null -> {} // still waiting for a fix
                    nmea -> {
                        appendConsole(NmeaAssembler.gga(l), LineKind.OUTPUT)
                        appendConsole(NmeaAssembler.rmc(l), LineKind.OUTPUT)
                    }
                    else -> appendConsole(
                        "◈ ${"%.6f".format(l.lat)}, ${"%.6f".format(l.lon)} · " +
                            "alt ${"%.1f".format(l.altMeters)} m · ±${"%.0f".format(l.accuracyMeters)} m",
                        LineKind.OUTPUT,
                    )
                }
                delay(1500)
            }
        }
    }

    private fun startTracking() {
        gpsTracking = true
        location.start()
        val path = captureSink.begin("tracker", CaptureFrame.TYPE_GPX)
        captureSink.appendBytes(GpxAssembler.TRACK_HEADER.toByteArray(Charsets.UTF_8))
        _capture.value = CaptureUiState(active = true, path = path, kind = "gpx")
        appendConsole("> gpstracker (phone GPS)", LineKind.INPUT)
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            // One track point per fresh fix.
            location.location.collectLatest { l ->
                if (l != null && captureSink.isActive) {
                    captureSink.appendBytes(GpxAssembler.trackPoint(l).toByteArray(Charsets.UTF_8))
                    _capture.value = _capture.value.copy(bytes = captureSink.bytes, rows = _capture.value.rows + 1)
                }
            }
        }
    }

    private fun stopTracking() {
        gpsTracking = false
        liveJob?.cancel(); liveJob = null
        if (captureSink.isActive) {
            captureSink.appendBytes(GpxAssembler.TRACK_FOOTER.toByteArray(Charsets.UTF_8))
            endCapture()
        }
        maybeStopLocation()
        appendConsole("Tracker stopped", LineKind.SYSTEM)
    }

    /** POI marking uses its own file (independent of [captureSink]) so it can span
     *  screens: -s opens it, -m adds a waypoint, -e closes it. */
    private fun handlePoi(cmd: String) {
        location.start()
        when {
            cmd.contains("-e") -> closePoi()
            cmd.contains("-m") -> {
                openPoiIfNeeded()
                val l = location.location.value
                if (l == null) appendConsole("No GPS fix yet — POI not marked", LineKind.SYSTEM)
                else {
                    poiCount++
                    runCatching { poiOut?.write(GpxAssembler.waypoint(l, "POI $poiCount").toByteArray(Charsets.UTF_8)) }
                    appendConsole("POI #$poiCount marked at ${"%.6f".format(l.lat)}, ${"%.6f".format(l.lon)}", LineKind.SYSTEM)
                }
            }
            else -> { openPoiIfNeeded(); appendConsole("POI session started (phone GPS)", LineKind.INPUT) }
        }
    }

    private fun openPoiIfNeeded() {
        if (poiOut != null) return
        runCatching {
            val dir = File(app.getExternalFilesDir(null), "captures").apply { mkdirs() }
            var i = 0
            var f: File
            do { f = File(dir, "poi_$i.gpx"); i++ } while (f.exists())
            poiOut = BufferedOutputStream(FileOutputStream(f)).also {
                it.write(GpxAssembler.POI_HEADER.toByteArray(Charsets.UTF_8))
            }
            poiPath = f.absolutePath
            poiCount = 0
            poiOpen = true
        }
    }

    private fun closePoi() {
        val out = poiOut ?: run { appendConsole("No POI session open", LineKind.SYSTEM); return }
        runCatching {
            out.write(GpxAssembler.POI_FOOTER.toByteArray(Charsets.UTF_8))
            out.flush(); out.close()
        }
        appendConsole("POI saved ($poiCount points): ${poiPath}", LineKind.SYSTEM)
        _snackbar.tryEmit("POI saved: $poiPath")
        poiOut = null; poiPath = null; poiOpen = false
        maybeStopLocation()
    }

    /** Turn the GNSS off only when no phone-GPS activity still needs it. */
    private fun maybeStopLocation() {
        if (!wardriving && !gpsData && !gpsTracking && !poiOpen) location.stop()
    }

    // --- On-phone capture helpers --------------------------------------------

    /** On the first proto ≥ 2 handshake, enable saving to serial.
     *
     *  We deliberately DO NOT raise the line rate. Raising to a high baud proved
     *  unreliable on the common CH340 USB-UART bridge: the device switches fine but
     *  the bridge can't sustain the rate, corrupting the link — and once the device
     *  is at the bad rate there is no way to command it back (a reconnect just
     *  re-raises into garbage). 115200 is rock-solid and handles portal uploads and
     *  header/handshake capture; a verified high-speed path can come back later. */
    private fun negotiateCapture(info: DeviceMessage.Info) {
        captureCapable = info.proto >= 2 || info.has("capstream")
        if (!captureCapable || negotiated) return
        negotiated = true
        viewModelScope.launch {
            usb.send("settings -s SavePCAP enable")
        }
    }

    private fun endCapture() {
        val summary = captureSink.end()
        _capture.value = _capture.value.copy(
            active = false,
            path = summary?.path ?: _capture.value.path,
            bytes = summary?.bytes ?: _capture.value.bytes,
            missedFrames = summary?.missedFrames ?: _capture.value.missedFrames,
        )
        summary?.let { _snackbar.tryEmit("Capture saved: ${it.bytes} B → ${it.path}") }
    }

    /** Wardrive using the phone's GPS: run a continuous AP scan and write a
     *  WigleWiFi CSV, tagging each newly-seen AP with the phone's current fix. */
    private fun startWardrive(command: String) {
        wardriving = true
        wardriveSeen.clear()
        val located = location.start()
        // WigleWiFi content in a .log file — same as the official Marauder's
        // /wardrive_N.log on SD (rename to .csv before a WiGLE upload).
        val path = captureSink.beginFile("wardrive", "log")
        captureSink.appendBytes(WardriveAssembler.header(APP_RELEASE).toByteArray(Charsets.UTF_8))
        _capture.value = CaptureUiState(active = true, path = path, kind = "wardrive")
        if (!located) _snackbar.tryEmit("Location permission/GPS off — rows will log without a fix")

        appendConsole("> $command (phone GPS)", LineKind.INPUT)
        usb.send("scanall")
        apBuf.clear()
        _listLoading.value = ListType.ACCESS_POINTS
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            delay(500)
            while (isActive && usb.status.value == UsbSerialManager.Status.CONNECTED) {
                usb.send("jsonstatus")
                usb.send("jsonlist a")
                delay(1300)
            }
        }
    }

    private fun stopWardrive() {
        wardriving = false
        maybeStopLocation()
    }

    /** Append a WigleWiFi row for every AP seen for the first time this session. */
    private fun onWardriveAps(list: List<DeviceMessage.Ap>) {
        val loc = location.location.value
        var added = 0
        for (ap in list) {
            if (ap.bssid.isBlank()) continue
            if (wardriveSeen.add(ap.bssid)) {
                captureSink.appendBytes(WardriveAssembler.line(ap, loc).toByteArray(Charsets.UTF_8))
                added++
            }
        }
        if (added > 0) {
            _capture.value = _capture.value.copy(bytes = captureSink.bytes, rows = wardriveSeen.size)
        }
    }

    fun clearConsole() {
        _console.value = emptyList()
    }

    // --- Firmware flashing ---------------------------------------------------

    /** USB serial adapters currently on the bus, offered as flash targets. */
    fun availableFlashDevices(): List<UsbSerialManager.DeviceOption> = usb.availableDevices()

    /** Clear a finished (done/failed) flash result so the screen returns to idle. */
    fun resetFlashState() {
        if (_flash.value.inProgress) return
        _flash.value = FlashUiState()
    }

    // --- Evil Portal (host-supplied HTML page) -------------------------------

    /** Reset the Evil Portal flow — call when the screen opens. */
    fun resetEvilPortal() {
        portalJob?.cancel()
        _evilPortal.value = EvilPortalUiState()
    }

    /** Choose which scanned AP (by list index) the portal impersonates. */
    fun setPortalTargetAp(index: Int?) {
        _evilPortal.value = _evilPortal.value.copy(targetApIndex = index)
    }

    /**
     * Stream a phone-picked HTML page into the device's Evil Portal buffer so no
     * SD card is needed. Sends `evilportal -c sethtmlstr <len>`, waits for the
     * device's `{"t":"portal","state":"recv"}` reply, streams exactly <len> bytes,
     * then verifies the `{"t":"portal","state":"set"}` reply (byte count + CRC-32).
     */
    fun uploadPortalHtml(bytes: ByteArray, fileName: String) {
        if (bytes.isEmpty()) { _snackbar.tryEmit("Empty HTML file"); return }
        portalJob?.cancel()
        portalJob = viewModelScope.launch {
            _evilPortal.value = _evilPortal.value.copy(
                phase = EvilPortalUiState.Phase.Uploading,
                fileName = fileName,
                htmlBytes = bytes.size,
                message = "Uploading ${bytes.size} B…",
            )
            var recv: DeviceMessage.Portal? = null
            var set: DeviceMessage.Portal? = null
            // Hold the serial write lock across the whole handshake so a background
            // `jsonstatus` poll can't inject bytes into the raw payload (which would
            // corrupt it and fail the device's CRC check).
            usb.withWriteLock {
                coroutineScope {
                    // Start listening BEFORE sending so the reply can't be missed.
                    val recvWait = async { withTimeoutOrNull(3000) { portalEvents.first { it.state == "recv" } } }
                    appendConsole("> evilportal -c sethtmlstr ${bytes.size}", LineKind.INPUT)
                    usb.sendLineLocked("evilportal -c sethtmlstr ${bytes.size}")
                    recv = recvWait.await()
                    appendConsole(
                        recv?.let { "portal ▸ recv ok (device max ${it.max} B)" }
                            ?: "portal ▸ recv TIMEOUT — no reply in 3 s",
                        LineKind.SYSTEM,
                    )
                    // Send the payload unless the device said its buffer is too small.
                    if (recv?.max?.let { it in 1 until bytes.size } != true) {
                        val setWait = async { withTimeoutOrNull(8000) { portalEvents.first { it.state == "set" } } }
                        appendConsole("portal ▸ streaming ${bytes.size} B…", LineKind.SYSTEM)
                        usb.sendRawLocked(bytes)
                        set = setWait.await()
                        appendConsole(
                            set?.let { "portal ▸ set n=${it.bytes} crc=${it.crc} ok=${it.ok}" }
                                ?: "portal ▸ set TIMEOUT — no confirm in 8 s",
                            LineKind.SYSTEM,
                        )
                    } else {
                        appendConsole("portal ▸ skipped: ${bytes.size} B > device max ${recv?.max} B", LineKind.SYSTEM)
                    }
                }
            }

            val r = recv
            val s = set
            val localCrc = Crc32.compute(bytes, 0, bytes.size, ByteArray(0), 0)
            _evilPortal.value = when {
                r != null && r.max in 1 until bytes.size -> _evilPortal.value.copy(
                    phase = EvilPortalUiState.Phase.Error,
                    deviceMax = r.max,
                    message = "Page too large: ${bytes.size} B > device max ${r.max} B",
                )
                s == null -> _evilPortal.value.copy(
                    phase = EvilPortalUiState.Phase.Error,
                    message = "No confirmation from device — check the cable and retry",
                )
                s.ok && s.crc == localCrc -> _evilPortal.value.copy(
                    phase = EvilPortalUiState.Phase.Ready,
                    deviceMax = if (s.max > 0) s.max else _evilPortal.value.deviceMax,
                    message = "Page set on device — ${s.bytes} B, CRC verified",
                )
                s.ok -> _evilPortal.value.copy(
                    phase = EvilPortalUiState.Phase.Error,
                    message = "CRC mismatch (device ${s.crc}, phone $localCrc) — retry",
                )
                else -> _evilPortal.value.copy(
                    phase = EvilPortalUiState.Phase.Error,
                    message = "Device rejected the upload (${s.bytes}/${bytes.size} B) — retry",
                )
            }
        }
    }

    /** Start the portal on the chosen AP (the page must already be uploaded). */
    fun startEvilPortal() {
        val st = _evilPortal.value
        if (!st.htmlReady) { _snackbar.tryEmit("Upload an HTML page first"); return }
        st.targetApIndex?.let { runCommand("evilportal -c setap $it") }
        runCommand("evilportal -c start")
        _evilPortal.value = st.copy(phase = EvilPortalUiState.Phase.Running, creds = emptyList())
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            delay(500)
            while (isActive && usb.status.value == UsbSerialManager.Status.CONNECTED) {
                usb.send("jsonstatus")
                delay(1300)
            }
        }
    }

    /** Stop the running portal (mirrors Back / stopscan on the device). */
    fun stopEvilPortal() {
        liveJob?.cancel(); liveJob = null
        runCommand("stopscan")
        if (_evilPortal.value.phase == EvilPortalUiState.Phase.Running) {
            _evilPortal.value = _evilPortal.value.copy(phase = EvilPortalUiState.Phase.Ready)
        }
    }

    /**
     * Download the firmware for [profile] from the pinned GitHub release and flash it
     * to [option] over USB. Frees the normal serial session first (the port can only
     * be held once). Progress and the final result are published on [flash].
     */
    fun startFlash(option: UsbSerialManager.DeviceOption, profile: FlashProfile = Firmware.DEFAULT) {
        if (flashJob?.isActive == true) return
        val app = getApplication<Application>()
        flashJob = viewModelScope.launch(Dispatchers.IO) {
            val link = UsbFlashLink(app, option.driver)
            try {
                setFlash(FlashStage.CONNECT, 0f, "Preparing…")
                if (usb.status.value != UsbSerialManager.Status.DISCONNECTED) {
                    usb.disconnect()
                    delay(400)
                }

                val repo = FirmwareRepository()
                val sums = repo.checksums()
                val images = profile.parts.map { part ->
                    setFlash(FlashStage.DOWNLOAD, 0f, "Downloading ${part.assetName}")
                    val bytes = repo.download(part.assetName) { f ->
                        setFlash(FlashStage.DOWNLOAD, f, "Downloading ${part.assetName} · ${(f * 100).toInt()}%")
                    }
                    sums[part.assetName]?.let { repo.verifySha256(part.assetName, bytes, it) }
                    part.offset to bytes
                }

                setFlash(FlashStage.CONNECT, 0f, "Opening USB port…")
                link.open()
                EspFlasher(link).flash(images) { p -> setFlash(p.stage, p.fraction, p.message) }
            } catch (e: Exception) {
                setFlash(FlashStage.ERROR, _flash.value.fraction, e.message ?: "Flash failed")
                _snackbar.tryEmit("Flash failed: ${e.message}")
            } finally {
                runCatching { link.close() }
            }
        }
    }

    private fun setFlash(stage: FlashStage, fraction: Float, message: String) {
        val cur = _flash.value
        val log = if (stage != cur.stage || cur.log.isEmpty()) (cur.log + message).takeLast(40) else cur.log
        _flash.value = FlashUiState(stage, fraction, message, log)
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
                negotiateCapture(msg)
            }
            is DeviceMessage.Status -> _deviceStatus.value = msg
            is DeviceMessage.JsonMode ->
                appendConsole("JSON mode ${if (msg.on) "enabled" else "disabled"}", LineKind.SYSTEM)
            is DeviceMessage.Baud -> {
                // The device confirmed the new rate (sent at the OLD rate) and has
                // switched; match it so the faster link is used from here on.
                usb.setBaud(msg.rate)
                appendConsole("Serial rate → ${msg.rate} baud", LineKind.SYSTEM)
            }
            is DeviceMessage.Drop -> {
                _capture.value = _capture.value.copy(
                    droppedPackets = _capture.value.droppedPackets + msg.n,
                )
            }
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
            is DeviceMessage.Portal -> portalEvents.tryEmit(msg)
            is DeviceMessage.Cred -> {
                _evilPortal.value = _evilPortal.value.copy(
                    creds = _evilPortal.value.creds + msg,
                )
                appendConsole("⚑ credential — ${msg.user} : ${msg.pass}", LineKind.OUTPUT)
            }
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
        // Close any device-fed capture and reset per-connection negotiation. Phone
        // GPS activities (tracker/POI) are independent of the device link, so leave
        // them running; only drop the GNSS if nothing still needs it.
        if (captureSink.isActive && !gpsTracking) captureSink.end()
        wardriving = false
        maybeStopLocation()
        negotiated = false
        captureCapable = false
        if (!gpsTracking) _capture.value = CaptureUiState()
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

        private const val APP_RELEASE = "marauder-mobile"
    }
}
