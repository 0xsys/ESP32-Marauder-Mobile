package com.marauder.mobile

import com.marauder.mobile.protocol.DeviceMessage
import com.marauder.mobile.protocol.LineParser
import com.marauder.mobile.protocol.ParsedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineParserTest {

    @Test
    fun plainLineIsConsole() {
        val parsed = LineParser.parse("Marauder booting...")
        assertTrue(parsed is ParsedLine.Console)
    }

    @Test
    fun parsesInfoHandshake() {
        val line = "@J {\"t\":\"info\",\"fw\":\"v1.2.3\",\"proto\":1,\"board\":\"ESP32\",\"caps\":[\"wifi\",\"bt\",\"gps\"]}"
        val parsed = LineParser.parse(line)
        assertTrue(parsed is ParsedLine.Structured)
        val msg = (parsed as ParsedLine.Structured).message
        assertTrue(msg is DeviceMessage.Info)
        msg as DeviceMessage.Info
        assertEquals("v1.2.3", msg.fw)
        assertEquals(1, msg.proto)
        assertEquals("ESP32", msg.board)
        assertTrue(msg.has("gps"))
        assertTrue(!msg.has("sd"))
    }

    @Test
    fun parsesStatus() {
        val line = "@J {\"t\":\"status\",\"mode\":49,\"running\":true,\"free\":123456,\"aps\":5,\"stas\":3,\"ssids\":2,\"ips\":0,\"probes\":7,\"airtags\":1}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Status
        assertEquals(49, msg.mode)
        assertTrue(msg.running)
        assertEquals(123456L, msg.free)
        assertEquals(5, msg.aps)
        assertEquals(7, msg.probes)
    }

    @Test
    fun parsesApRowWithUnicodeEssid() {
        val line = "@J {\"t\":\"ap\",\"i\":0,\"ch\":6,\"rssi\":-42,\"sel\":true,\"pkts\":10,\"sec\":3,\"wps\":false,\"nsta\":2,\"bssid\":\"aa:bb:cc:dd:ee:ff\",\"essid\":\"Café\"}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Ap
        assertEquals(0, msg.index)
        assertEquals(6, msg.channel)
        assertEquals(-42, msg.rssi)
        assertTrue(msg.selected)
        assertEquals("aa:bb:cc:dd:ee:ff", msg.bssid)
        assertEquals("Café", msg.essid)
    }

    @Test
    fun parsesEndMarker() {
        val msg = (LineParser.parse("@J {\"t\":\"end\",\"list\":\"a\",\"n\":5}") as ParsedLine.Structured).message
        assertTrue(msg is DeviceMessage.End)
        assertEquals(5, (msg as DeviceMessage.End).count)
        assertEquals("a", msg.list)
    }

    @Test
    fun malformedStructuredJsonIsFlagged() {
        val parsed = LineParser.parse("@J {not valid")
        assertTrue(parsed is ParsedLine.Malformed)
    }

    @Test
    fun parsesStatusEvenWithGluedFirmwarePrompt() {
        // The firmware's "> " prompt (no newline) can glue onto a reply.
        val line = "> @J {\"t\":\"status\",\"mode\":0,\"running\":false,\"free\":145856,\"aps\":0,\"stas\":0,\"ssids\":0,\"ips\":0,\"probes\":6,\"airtags\":0}"
        val parsed = LineParser.parse(line)
        assertTrue(parsed is ParsedLine.Structured)
        val msg = (parsed as ParsedLine.Structured).message as DeviceMessage.Status
        assertEquals(0, msg.mode)
        assertEquals(6, msg.probes)
        assertEquals(145856L, msg.free)
    }

    @Test
    fun plainPromptedLineStaysConsole() {
        // A "> "-prefixed human line without the structured tag is still console.
        val parsed = LineParser.parse("> some human output")
        assertTrue(parsed is ParsedLine.Console)
        assertEquals("> some human output", (parsed as ParsedLine.Console).raw)
    }

    @Test
    fun parsesChannelActivityArrays() {
        val line = "@J {\"t\":\"chan\",\"page\":1,\"ch\":[1,6,11],\"v\":[3,7,2]}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.ChannelActivity
        assertEquals(listOf(1, 6, 11), msg.channels)
        assertEquals(listOf(3, 7, 2), msg.values)
        assertEquals(1, msg.page)
    }
}
