package com.marauder.mobile.protocol

/**
 * One decoded device message. Mirrors the `@J {"t":...}` objects the firmware
 * emits from JsonSerial.cpp (info / status / jsonmode / list rows / end / err /
 * analyzer streaming).
 */
sealed interface DeviceMessage {

    data class Info(
        val fw: String,
        val proto: Int,
        val board: String,
        val caps: List<String>,
    ) : DeviceMessage {
        fun has(cap: String) = caps.any { it.equals(cap, ignoreCase = true) }
    }

    data class Status(
        val mode: Int,
        val running: Boolean,
        val free: Long,
        val aps: Int,
        val stations: Int,
        val ssids: Int,
        val ips: Int,
        val probes: Int,
        val airtags: Int,
        /** Remote ID (drone) record count. Proto >= 3; 0 on older firmware. */
        val rid: Int = 0,
        /** CSI motion detection running. Proto >= 3 and CSI-capable boards only. */
        val csi: Boolean = false,
    ) : DeviceMessage

    data class JsonMode(val on: Boolean) : DeviceMessage

    /** Confirms a `jsonbaud` line-rate change; sent at the OLD rate, then the
     *  device switches, so the host re-opens its port at [rate] on receipt. */
    data class Baud(val rate: Int) : DeviceMessage

    /** Reports packets the device's capture ring dropped since the last flush. */
    data class Drop(val n: Long) : DeviceMessage

    data class Ap(
        val index: Int,
        val channel: Int,
        val rssi: Int,
        val selected: Boolean,
        val packets: Long,
        val sec: Int,
        val wps: Boolean,
        val stations: Int,
        val bssid: String,
        val essid: String,
    ) : DeviceMessage

    data class Sta(
        val ap: Int,
        val index: Int,
        val selected: Boolean,
        val packets: Long,
        val mac: String,
    ) : DeviceMessage

    data class SsidRow(
        val index: Int,
        val channel: Int,
        val selected: Boolean,
        val essid: String,
    ) : DeviceMessage

    data class Ip(val index: Int, val ip: String) : DeviceMessage

    data class Probe(
        val index: Int,
        val requests: Long,
        val selected: Boolean,
        val essid: String,
    ) : DeviceMessage

    data class Airtag(
        val index: Int,
        val rssi: Int,
        val selected: Boolean,
        val mac: String,
    ) : DeviceMessage

    /** Closes a `jsonlist` stream: which list, and how many rows preceded it. */
    data class End(val list: String, val count: Int) : DeviceMessage

    data class Err(val cmd: String, val arg: String) : DeviceMessage

    /** Evil Portal host-supplied page (proto >= 2). `state` is "recv" (device
     *  ready, [max] = buffer capacity) then "set" ([bytes] stored, [crc] of the
     *  received bytes, [ok] when the upload matched). */
    data class Portal(
        val state: String,
        val bytes: Int,
        val max: Int,
        val crc: Long,
        val ok: Boolean,
    ) : DeviceMessage

    /** One captured Evil Portal form submission (username + password). */
    data class Cred(val user: String, val pass: String) : DeviceMessage

    /** One rolling-graph sample (Channel Analyzer / BT Analyzer). */
    data class AnalyzerSample(val mode: Int, val channel: Int, val value: Int) : DeviceMessage

    /** A page of the Channel Summary bar chart. */
    data class ChannelActivity(
        val page: Int,
        val channels: List<Int>,
        val values: List<Int>,
    ) : DeviceMessage

    // --- proto >= 3 (v1.20.0 features) ---------------------------------------

    /** One detected Remote ID (drone) record (`jsonlist d`). [lat]/[lon] are
     *  1e-7 degree integers (divide by 1e7); valid only when [hasLocation]. */
    data class Rid(
        val index: Int,
        val mac: String,
        val uas: String,
        val op: String,
        val lat: Long,
        val lon: Long,
        val altMeters: Double,
        val speedMps: Double,
        val directionDeg: Int,
        val rssi: Int,
        val uaType: Int,
        val packets: Long,
        val hasLocation: Boolean,
    ) : DeviceMessage {
        val latitude: Double get() = lat / 1e7
        val longitude: Double get() = lon / 1e7
    }

    /** One configured geofence (`jsonlist g`), 1-based [index] 1..5. */
    data class Fence(
        val index: Int,
        val enabled: Boolean,
        val lat: Double,
        val lon: Double,
        val radiusMiles: Double,
        val name: String,
    ) : DeviceMessage

    /** A live Fox Hunt signal-strength sample for the current target. */
    data class FoxHunt(
        val name: String,
        val rssi: Int,
        val channel: Int,
        val bt: Boolean,
    ) : DeviceMessage

    /** A CSI-motion status sample (~1 Hz). [motion]/[reliability]/[calibration]
     *  are 0..100 percentages; [rate] is samples/sec. */
    data class Csi(
        val state: String,
        val motion: Int,
        val reliability: Int,
        val rate: Int,
        val calibration: Int,
        val channel: Int,
        val ap: String,
    ) : DeviceMessage

    data class Unknown(val type: String) : DeviceMessage
}

/** Result of decoding a single serial line. */
sealed interface ParsedLine {
    val raw: String

    /** A well-formed `@J ` structured message. */
    data class Structured(override val raw: String, val message: DeviceMessage) : ParsedLine

    /** Ordinary human-readable firmware output (no `@J ` prefix). */
    data class Console(override val raw: String) : ParsedLine

    /** Had the `@J ` prefix but the JSON body could not be decoded. */
    data class Malformed(override val raw: String, val error: String) : ParsedLine
}
