package com.marauder.mobile.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Splits a raw serial line into either a structured device message (lines
 * that start with the `@J ` tag defined in JsonSerial.h) or plain console text.
 * Never throws: malformed structured JSON is surfaced as [ParsedLine.Malformed].
 */
object LineParser {

    const val PREFIX = "@J "

    // The firmware re-prints an interactive prompt ("> ", no trailing newline)
    // after each command a human might type. When a structured reply follows, the
    // prompt glues onto the front of the line ("> @J {...}"). Strip any such
    // leading prompt(s) before testing for the structured tag so those replies are
    // still recognised as structured rather than dumped into the console.
    private val LEADING_PROMPT = Regex("^(?:> )+")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(line: String): ParsedLine {
        val raw = line.trimEnd('\r', '\n')
        val tagged = raw.trimStart().replaceFirst(LEADING_PROMPT, "")
        if (!tagged.startsWith(PREFIX)) return ParsedLine.Console(raw)

        val body = tagged.substring(PREFIX.length).trim()
        val obj = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return ParsedLine.Malformed(raw, e.message ?: "invalid JSON")
        }

        val type = obj.str("t") ?: return ParsedLine.Malformed(raw, "missing \"t\"")

        val message: DeviceMessage = when (type) {
            "info" -> DeviceMessage.Info(
                fw = obj.str("fw").orEmpty(),
                proto = obj.int("proto"),
                board = obj.str("board").orEmpty(),
                caps = obj.strList("caps"),
            )

            "status" -> DeviceMessage.Status(
                mode = obj.int("mode"),
                running = obj.bool("running"),
                free = obj.long("free"),
                aps = obj.int("aps"),
                stations = obj.int("stas"),
                ssids = obj.int("ssids"),
                ips = obj.int("ips"),
                probes = obj.int("probes"),
                airtags = obj.int("airtags"),
            )

            "jsonmode" -> DeviceMessage.JsonMode(on = obj.bool("on"))

            "ap" -> DeviceMessage.Ap(
                index = obj.int("i"),
                channel = obj.int("ch"),
                rssi = obj.int("rssi"),
                selected = obj.bool("sel"),
                packets = obj.long("pkts"),
                sec = obj.int("sec"),
                wps = obj.bool("wps"),
                stations = obj.int("nsta"),
                bssid = obj.str("bssid").orEmpty(),
                essid = obj.str("essid").orEmpty(),
            )

            "sta" -> DeviceMessage.Sta(
                ap = obj.int("ap"),
                index = obj.int("i"),
                selected = obj.bool("sel"),
                packets = obj.long("pkts"),
                mac = obj.str("mac").orEmpty(),
            )

            "ssid" -> DeviceMessage.SsidRow(
                index = obj.int("i"),
                channel = obj.int("ch"),
                selected = obj.bool("sel"),
                essid = obj.str("essid").orEmpty(),
            )

            "ip" -> DeviceMessage.Ip(
                index = obj.int("i"),
                ip = obj.str("ip").orEmpty(),
            )

            "probe" -> DeviceMessage.Probe(
                index = obj.int("i"),
                requests = obj.long("req"),
                selected = obj.bool("sel"),
                essid = obj.str("essid").orEmpty(),
            )

            "airtag" -> DeviceMessage.Airtag(
                index = obj.int("i"),
                rssi = obj.int("rssi"),
                selected = obj.bool("sel"),
                mac = obj.str("mac").orEmpty(),
            )

            "end" -> DeviceMessage.End(
                list = obj.str("list").orEmpty(),
                count = obj.int("n"),
            )

            "err" -> DeviceMessage.Err(
                cmd = obj.str("cmd").orEmpty(),
                arg = obj.str("arg").orEmpty(),
            )

            "asample" -> DeviceMessage.AnalyzerSample(
                mode = obj.int("mode"),
                channel = obj.int("ch"),
                value = obj.int("v"),
            )

            "chan" -> DeviceMessage.ChannelActivity(
                page = obj.int("page"),
                channels = obj.intList("ch"),
                values = obj.intList("v"),
            )

            else -> DeviceMessage.Unknown(type)
        }

        return ParsedLine.Structured(raw, message)
    }
}

// --- Small null-safe JsonObject accessors ------------------------------------

private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull

private fun JsonObject.int(key: String): Int = prim(key)?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long = prim(key)?.longOrNull ?: 0L

private fun JsonObject.bool(key: String): Boolean = prim(key)?.booleanOrNull ?: false

private fun JsonObject.strList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

private fun JsonObject.intList(key: String): List<Int> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList()
