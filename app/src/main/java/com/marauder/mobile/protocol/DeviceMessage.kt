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
    ) : DeviceMessage

    data class JsonMode(val on: Boolean) : DeviceMessage

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

    /** One rolling-graph sample (Channel Analyzer / BT Analyzer). */
    data class AnalyzerSample(val mode: Int, val channel: Int, val value: Int) : DeviceMessage

    /** A page of the Channel Summary bar chart. */
    data class ChannelActivity(
        val page: Int,
        val channels: List<Int>,
        val values: List<Int>,
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
