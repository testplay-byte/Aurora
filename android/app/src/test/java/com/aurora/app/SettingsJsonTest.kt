package com.aurora.app

import com.aurora.app.ble.GeneralSettings
import com.aurora.app.ble.ModeEditorState
import com.aurora.app.ble.Rgb
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {

    // ------------------------------------------------------------- GENERAL

    @Test
    fun `general defaults match the protocol doc`() {
        val g = GeneralSettings()
        val json = g.toJson()
        assertFalse(json.getBoolean("red_limit_enabled"))
        assertEquals(255, json.getInt("red_limit_value"))
        assertFalse(json.getBoolean("auto_turn_on_enabled"))
        assertEquals("18:00", json.getString("auto_turn_on_time"))
        assertFalse(json.getBoolean("auto_turn_off_enabled"))
        assertEquals("23:00", json.getString("auto_turn_off_time"))
        assertEquals(3, json.getInt("short_blink_count"))
        assertEquals(200, json.getInt("short_blink_speed"))
        assertEquals(128, json.getInt("short_alert_brightness"))
    }

    @Test
    fun `general patch wraps under GENERAL for deep merge`() {
        val patch = GeneralSettings(redLimitEnabled = true, redLimitValue = 120).toPatchJson()
        val general = patch.getJSONObject("GENERAL")
        assertTrue(general.getBoolean("red_limit_enabled"))
        assertEquals(120, general.getInt("red_limit_value"))
    }

    @Test
    fun `general parses from full settings root and tolerates missing keys`() {
        val root = JSONObject("""{"GENERAL":{"red_limit_value":99},"MODE_1":{"name":"SOLID"}}""")
        val g = GeneralSettings.fromSettingsRoot(root)
        assertEquals(99, g.redLimitValue)
        assertEquals(3, g.shortBlinkCount) // default when absent

        val empty = GeneralSettings.fromSettingsRoot(JSONObject())
        assertEquals(GeneralSettings(), empty)
    }

    @Test
    fun `time regex accepts 24h times and rejects junk`() {
        assertTrue(GeneralSettings.TIME_REGEX.matches("00:00"))
        assertTrue(GeneralSettings.TIME_REGEX.matches("18:00"))
        assertTrue(GeneralSettings.TIME_REGEX.matches("23:59"))
        assertFalse(GeneralSettings.TIME_REGEX.matches("24:00"))
        assertFalse(GeneralSettings.TIME_REGEX.matches("9:5"))
        assertFalse(GeneralSettings.TIME_REGEX.matches("18:0"))
        assertFalse(GeneralSettings.TIME_REGEX.matches("abc"))
    }

    // -------------------------------------------------------------- modes

    @Test
    fun `solid serializes exactly per schema`() {
        val json = ModeEditorState.Solid(listOf(Rgb(1, 2, 3), Rgb(4, 5, 6), Rgb(7, 8, 9), Rgb(10, 11, 12))).toJson()
        assertEquals("SOLID", json.getString("name"))
        val colors = json.getJSONArray("colors")
        assertEquals(4, colors.length())
        assertEquals(3, colors.getJSONArray(0).length())
        assertEquals(1, colors.getJSONArray(0).getInt(0))
    }

    @Test
    fun `blink schema keys`() {
        val json = ModeEditorState.Blink(interval = 700, noOff = true).toJson()
        assertEquals("BLINK", json.getString("name"))
        assertEquals(700, json.getInt("interval"))
        assertTrue(json.getBoolean("no_off"))
        assertTrue(json.has("colors"))
        // firmware never reads "random" — the app must not send it
        assertFalse(json.has("random"))
    }

    @Test
    fun `wave schema pins diagonal pattern`() {
        val json = ModeEditorState.Wave(interval = 40).toJson()
        assertEquals("WAVE", json.getString("name"))
        assertEquals(40, json.getInt("interval"))
        assertEquals("diagonal", json.getString("pattern"))
    }

    @Test
    fun `glow schema keys`() {
        val json = ModeEditorState.Glow(interval = 25, smoothTransitions = true, breathingEffect = false).toJson()
        assertEquals("GLOW", json.getString("name"))
        assertEquals(25, json.getInt("interval"))
        assertTrue(json.getBoolean("smooth_transitions"))
        assertFalse(json.getBoolean("breathing_effect"))
    }

    @Test
    fun `trail schema keys`() {
        val json = ModeEditorState.Trail(speed = 2500, direction = false, trailLength = 2, brightness = 60).toJson()
        assertEquals("TRAIL", json.getString("name"))
        assertEquals(2500, json.getInt("speed"))
        assertFalse(json.getBoolean("direction"))
        assertEquals(2, json.getInt("trail_length"))
        assertEquals(60, json.getInt("brightness"))
    }

    @Test
    fun `spiral schema keys and pause flag`() {
        val json = ModeEditorState.Spiral(speed = 800, paused = true).toJson()
        assertEquals("SPIRAL", json.getString("name"))
        assertEquals(800, json.getInt("speed"))
        assertTrue(json.getBoolean("paused"))
        assertTrue(json.getBoolean("direction"))
        assertEquals(100, json.getInt("brightness"))
    }

    // ------------------------------------------- parse doc examples → state

    @Test
    fun `fromJson parses the documented blink example`() {
        val doc = """{"name":"BLINK","interval":500,"random":true,"no_off":false,"colors":[[255,0,0],[0,255,0]]}"""
        val s = ModeEditorState.fromJson(2, doc) as ModeEditorState.Blink
        assertEquals(500, s.interval)
        assertFalse(s.noOff) // "random" key ignored — firmware never reads it
        assertEquals(2, s.colors.size)
        assertEquals(Rgb(255, 0, 0), s.colors[0])
    }

    @Test
    fun `fromJson parses the documented trail example`() {
        val doc = """{"name":"TRAIL","speed":1000,"direction":true,"trail_length":3,"brightness":100,"colors":[[1,2,3]]}"""
        val s = ModeEditorState.fromJson(5, doc) as ModeEditorState.Trail
        assertEquals(1000, s.speed)
        assertTrue(s.direction)
        assertEquals(3, s.trailLength)
        assertEquals(100, s.brightness)
    }

    @Test
    fun `fromJson clamps out-of-range values`() {
        val s = ModeEditorState.fromJson(5, """{"speed":-5,"brightness":900,"trail_length":99}""") as ModeEditorState.Trail
        assertEquals(100, s.speed)      // clamped to range floor
        assertEquals(100, s.brightness) // clamped to range ceiling
        assertEquals(4, s.trailLength)  // clamped to 1..4

        val spiral = ModeEditorState.fromJson(6, """{"speed":10}""") as ModeEditorState.Spiral
        assertEquals(50, spiral.speed)  // firmware floor is 50 ms (ModeSpiral.cpp)
    }

    @Test
    fun `fromJson falls back to defaults on garbage`() {
        val s = ModeEditorState.fromJson(3, "not json at all")
        assertTrue(s is ModeEditorState.Wave)
    }

    @Test
    fun `solid from json always yields four corners`() {
        val s = ModeEditorState.fromJson(1, """{"name":"SOLID","colors":[[9,9,9]]}""") as ModeEditorState.Solid
        assertEquals(4, s.corners.size)
        assertEquals(Rgb(9, 9, 9), s.corners[0])
    }

    @Test
    fun `spiral from json always yields four colors`() {
        val s = ModeEditorState.fromJson(6, """{"name":"SPIRAL","colors":[[1,1,1],[2,2,2]]}""") as ModeEditorState.Spiral
        assertEquals(4, s.colors.size)
    }

    @Test
    fun `round trip state to json to state preserves values`() {
        val original = ModeEditorState.Blink(interval = 333, noOff = true,
            colors = listOf(Rgb(1, 2, 3), Rgb(4, 5, 6)))
        val parsed = ModeEditorState.fromJson(2, original.toJson().toString()) as ModeEditorState.Blink
        assertEquals(original, parsed)
    }

    // ---------------------------------------------------------------- colors

    @Test
    fun `rgb clamps and hex formats`() {
        assertEquals(Rgb(255, 0, 0), Rgb(300, -5, 0).clamped())
        assertEquals("#00C896", Rgb(0, 200, 150).toHex())
    }

    // -------------------------------------------------------- dirty-field save

    @Test
    fun `toJsonFor emits only the requested keys`() {
        val patch = GeneralSettings(redLimitEnabled = true, redLimitValue = 42)
            .toPatchFor(setOf("red_limit_value"))
        val general = patch.getJSONObject("GENERAL")
        assertEquals(1, general.length())
        assertEquals(42, general.getInt("red_limit_value"))
        assertFalse(general.has("red_limit_enabled"))
        assertFalse(general.has("short_blink_count"))
    }
}
