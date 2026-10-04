package com.aurora.app

import com.aurora.app.ble.BleProtocol
import com.aurora.app.ble.BleProtocol.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleProtocolTest {

    // ------------------------------------------------------------ wire format

    @Test
    fun `encode appends newline and uses utf8`() {
        val bytes = BleProtocol.encode("PING")
        assertEquals("PING\n", String(bytes, Charsets.UTF_8))
    }

    // -------------------------------------------------------- command builders
    // Exact strings from docs/BLE-PROTOCOL.md — any drift breaks the firmware.

    @Test
    fun `all twelve commands match the spec`() {
        assertEquals("GET:MODE", BleProtocol.getMode())
        assertEquals("SET:MODE:3", BleProtocol.setMode(3))
        assertEquals("OFF", BleProtocol.off())
        assertEquals("LED:2:255,128,0", BleProtocol.led(2, 255, 128, 0))
        assertEquals("ALL:10,20,30", BleProtocol.all(10, 20, 30))
        assertEquals("SAVE", BleProtocol.save())
        assertEquals("STATUS", BleProtocol.status())
        assertEquals("PING", BleProtocol.ping())
        assertEquals("GETSETTINGS", BleProtocol.getSettings())
        assertEquals("""SETSETTINGS:{"GENERAL":{"red_limit_value":100}}""",
            BleProtocol.setSettings("""{"GENERAL":{"red_limit_value":100}}"""))
        assertEquals("GET:MODESETTINGS:5", BleProtocol.getModeSettings(5))
        assertEquals("""SET:MODESETTINGS:4:{"name":"GLOW"}""",
            BleProtocol.setModeSettings(4, """{"name":"GLOW"}"""))
    }

    // ---------------------------------------------------------------- parsers

    @Test
    fun `parse MODE and STATUS`() {
        assertEquals(Response.ModeValue(3, "WAVE"), BleProtocol.parse("MODE:3:WAVE"))
        assertEquals(Response.ModeValue(0, "OFF"), BleProtocol.parse("STATUS:0:OFF"))
        assertEquals(Response.ModeValue(6, "SPIRAL"), BleProtocol.parse("MODE:6:SPIRAL\n"))
    }

    @Test
    fun `parse OK variants`() {
        assertEquals(Response.Ok("SAVED"), BleProtocol.parse("OK:SAVED"))
        assertEquals(Response.Ok("MODE:2:BLINK"), BleProtocol.parse("OK:MODE:2:BLINK"))
        assertEquals(Response.Ok("OFF"), BleProtocol.parse("OK:OFF"))
        assertEquals(Response.Ok("LED:0:255,0,0"), BleProtocol.parse("OK:LED:0:255,0,0"))
        assertEquals(Response.Ok("SETTINGS_SAVED"), BleProtocol.parse("OK:SETTINGS_SAVED"))
        assertEquals(Response.Ok("MODESETTINGS_SAVED:4"), BleProtocol.parse("OK:MODESETTINGS_SAVED:4"))
    }

    @Test
    fun `parse errors`() {
        assertEquals(Response.Error("INVALID_PARAMS"), BleProtocol.parse("ERR:INVALID_PARAMS"))
        assertEquals(Response.Error("UNKNOWN_CMD"), BleProtocol.parse("ERR:UNKNOWN_CMD"))
        assertEquals(Response.Error("INVALID_JSON"), BleProtocol.parse("ERR:INVALID_JSON"))
    }

    @Test
    fun `parse SETTINGS keeps json intact including colons`() {
        val json = """{"GENERAL":{"auto_turn_on_time":"18:00"},"MODE_1":{"name":"SOLID"}}"""
        val r = BleProtocol.parse("SETTINGS:$json")
        assertEquals(Response.Settings(json), r)
    }

    @Test
    fun `parse MODESETTINGS splits mode then json`() {
        val json = """{"name":"BLINK","interval":500,"colors":[[255,0,0]]}"""
        val r = BleProtocol.parse("MODESETTINGS:2:$json")
        assertEquals(Response.ModeSettings(2, json), r)
    }

    @Test
    fun `parse PONG and unknown`() {
        assertEquals(Response.Pong, BleProtocol.parse("PONG"))
        assertTrue(BleProtocol.parse("something weird") is Response.Unknown)
        assertTrue(BleProtocol.parse("MODE:xx:WAVE") is Response.Unknown)
        assertTrue(BleProtocol.parse("MODESETTINGS::{}") is Response.Unknown)
    }

    // ------------------------------------------------------------- line buffer

    @Test
    fun `firmware responses have no newline - each payload delivers whole`() {
        // Ble.cpp bleRespond sends bare strings: "PONG", "OK:OFF", …
        val buf = BleProtocol.LineBuffer()
        assertEquals(listOf("PONG"), buf.feed("PONG"))
        assertEquals(listOf("MODE:3:WAVE"), buf.feed("MODE:3:WAVE"))
        assertEquals(listOf("OK:MODESETTINGS_SAVED:4"), buf.feed("OK:MODESETTINGS_SAVED:4"))
    }

    @Test
    fun `line buffer still splits newline-bundled payloads and trims blanks`() {
        val buf = BleProtocol.LineBuffer()
        assertEquals(
            listOf("MODE:1:SOLID", "OK:SAVED"),
            buf.feed("MODE:1:SOLID\nOK:SAVED\n")
        )
        assertEquals(emptyList<String>(), buf.feed("   \n"))
        assertEquals(emptyList<String>(), buf.feed(""))
    }

    // ------------------------------------------------------------ name filter

    @Test
    fun `device name filter matches Aurora only`() {
        assertTrue(BleProtocol.matchesDeviceName("Aurora"))
        assertTrue(BleProtocol.matchesDeviceName("Aurora-Test-2"))
        assertTrue(!BleProtocol.matchesDeviceName("NTLWC-LED"))
        assertTrue(!BleProtocol.matchesDeviceName(null))
        assertTrue(!BleProtocol.matchesDeviceName("RandomTag"))
    }

    @Test
    fun `mode name table matches firmware`() {
        assertEquals(listOf("OFF", "SOLID", "BLINK", "WAVE", "GLOW", "TRAIL", "SPIRAL"),
            BleProtocol.MODE_NAMES.toList())
    }

    @Test
    fun `uuids match the docs`() {
        assertEquals("00004faf-0000-1000-8000-00805f9b34fb", BleProtocol.SERVICE_UUID)
        assertEquals("0000beb5-0000-1000-8000-00805f9b34fb", BleProtocol.CHAR_WRITE_UUID)
        assertEquals("0000beb6-0000-1000-8000-00805f9b34fb", BleProtocol.CHAR_NOTIFY_UUID)
    }
}
