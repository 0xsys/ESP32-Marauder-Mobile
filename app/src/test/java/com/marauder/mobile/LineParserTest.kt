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

    @Test
    fun parsesEvilPortalUploadConfirm() {
        val line = "@J {\"t\":\"portal\",\"state\":\"set\",\"n\":2048,\"crc\":3735928559,\"ok\":true}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Portal
        assertEquals("set", msg.state)
        assertEquals(2048, msg.bytes)
        assertEquals(3735928559L, msg.crc)
        assertTrue(msg.ok)
    }

    @Test
    fun parsesCapturedCredential() {
        // Escaped characters in the submitted values must survive decoding.
        val line = "@J {\"t\":\"cred\",\"u\":\"a\\\"b\",\"p\":\"p@ss w0rd\"}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Cred
        assertEquals("a\"b", msg.user)
        assertEquals("p@ss w0rd", msg.pass)
    }

    // --- proto >= 3 (v1.20.0) ------------------------------------------------

    @Test
    fun parsesStatusProto3Counters() {
        val line = "@J {\"t\":\"status\",\"mode\":89,\"running\":true,\"free\":100,\"aps\":0,\"stas\":0,\"ssids\":0,\"ips\":0,\"probes\":0,\"airtags\":0,\"rid\":3,\"csi\":true}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Status
        assertEquals(3, msg.rid)
        assertTrue(msg.csi)
    }

    @Test
    fun statusWithoutProto3CountersDefaultsToZero() {
        // Old firmware (proto < 3) omits rid/csi; parser must default, not crash.
        val line = "@J {\"t\":\"status\",\"mode\":0,\"running\":false,\"free\":1,\"aps\":0,\"stas\":0,\"ssids\":0,\"ips\":0,\"probes\":0,\"airtags\":0}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Status
        assertEquals(0, msg.rid)
        assertTrue(!msg.csi)
    }

    @Test
    fun parsesRemoteIdDrone() {
        val line = "@J {\"t\":\"rid\",\"i\":2,\"mac\":\"aa:bb:cc:dd:ee:ff\",\"uas\":\"UAS-123\",\"op\":\"OP-9\",\"lat\":377749000,\"lon\":-1224194000,\"alt\":120.5,\"spd\":4.2,\"dir\":270,\"rssi\":-55,\"ua\":2,\"pkts\":17,\"loc\":true}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Rid
        assertEquals(2, msg.index)
        assertEquals("UAS-123", msg.uas)
        assertEquals(377749000L, msg.lat)
        assertEquals(-1224194000L, msg.lon)
        assertEquals(37.7749, msg.latitude, 1e-6)
        assertEquals(-122.4194, msg.longitude, 1e-6)
        assertEquals(120.5, msg.altMeters, 1e-6)
        assertEquals(270, msg.directionDeg)
        assertTrue(msg.hasLocation)
    }

    @Test
    fun parsesGeofence() {
        val line = "@J {\"t\":\"fence\",\"i\":1,\"en\":true,\"lat\":37.774900,\"lon\":-122.419400,\"r\":0.50,\"name\":\"Home\"}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Fence
        assertEquals(1, msg.index)
        assertTrue(msg.enabled)
        assertEquals(37.7749, msg.lat, 1e-6)
        assertEquals(0.5, msg.radiusMiles, 1e-6)
        assertEquals("Home", msg.name)
    }

    @Test
    fun parsesFoxHuntSample() {
        val line = "@J {\"t\":\"fox\",\"name\":\"Target AP\",\"rssi\":-63,\"ch\":6,\"bt\":false}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.FoxHunt
        assertEquals("Target AP", msg.name)
        assertEquals(-63, msg.rssi)
        assertEquals(6, msg.channel)
        assertTrue(!msg.bt)
    }

    @Test
    fun parsesCsiSample() {
        val line = "@J {\"t\":\"csi\",\"state\":\"MOTION\",\"motion\":82,\"rel\":90,\"rate\":60,\"cal\":100,\"ch\":1,\"ap\":\"MyAP\"}"
        val msg = (LineParser.parse(line) as ParsedLine.Structured).message as DeviceMessage.Csi
        assertEquals("MOTION", msg.state)
        assertEquals(82, msg.motion)
        assertEquals(90, msg.reliability)
        assertEquals(60, msg.rate)
        assertEquals("MyAP", msg.ap)
    }
}
