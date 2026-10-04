package com.aurora.app.ble

import org.json.JSONArray
import org.json.JSONObject

/** A single RGB color (0..255 per channel). */
data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun clamped() = Rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    fun toHex(): String = "#%02X%02X%02X".format(clamped().r, clamped().g, clamped().b)
    fun toJsonArray() = JSONArray().put(r.coerceIn(0, 255)).put(g.coerceIn(0, 255)).put(b.coerceIn(0, 255))

    companion object {
        val DEFAULT_CORNERS = listOf(
            Rgb(255, 0, 0),      // TL
            Rgb(255, 255, 0),    // TR
            Rgb(255, 192, 203),  // BL
            Rgb(0, 0, 255)       // BR
        )
        val DEFAULT_PALETTE = listOf(Rgb(255, 0, 0), Rgb(0, 255, 0), Rgb(0, 0, 255), Rgb(255, 255, 0))

        fun fromJsonArray(a: JSONArray?): Rgb? {
            if (a == null || a.length() < 3) return null
            return Rgb(a.getInt(0), a.getInt(1), a.getInt(2)).clamped()
        }

        fun listToJson(list: List<Rgb>): JSONArray {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonArray()) }
            return arr
        }

        fun listFromJson(arr: JSONArray?, fallback: List<Rgb>): List<Rgb> {
            if (arr == null) return fallback
            val out = ArrayList<Rgb>(arr.length())
            for (i in 0 until arr.length()) {
                val c = fromJsonArray(arr.optJSONArray(i)) ?: continue
                out.add(c)
            }
            return out.ifEmpty { fallback }
        }
    }
}

/** `GENERAL` block — keys must match docs/BLE-PROTOCOL.md exactly. */
data class GeneralSettings(
    val redLimitEnabled: Boolean = false,
    val redLimitValue: Int = 255,
    val autoOnEnabled: Boolean = false,
    val autoOnTime: String = "18:00",
    val autoOffEnabled: Boolean = false,
    val autoOffTime: String = "23:00",
    val shortBlinkCount: Int = 3,
    val shortBlinkSpeed: Int = 200,
    val shortAlertBrightness: Int = 128
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("red_limit_enabled", redLimitEnabled)
        put("red_limit_value", redLimitValue)
        put("auto_turn_on_enabled", autoOnEnabled)
        put("auto_turn_on_time", autoOnTime)
        put("auto_turn_off_enabled", autoOffEnabled)
        put("auto_turn_off_time", autoOffTime)
        put("short_blink_count", shortBlinkCount)
        put("short_blink_speed", shortBlinkSpeed)
        put("short_alert_brightness", shortAlertBrightness)
    }

    /** Payload for SETSETTINGS: wraps under GENERAL (deep-merged by firmware). */
    fun toPatchJson(): JSONObject = JSONObject().put("GENERAL", toJson())

    /**
     * Patch containing ONLY [keys] — because BLE GETSETTINGS responses can be
     * truncated (full dump > one notify), the app never blindly re-sends the
     * whole block; only fields the user actually touched are written back.
     */
    fun toJsonFor(keys: Set<String>): JSONObject {
        val full = toJson()
        val out = JSONObject()
        for (k in full.keys()) {
            if (k in keys) out.put(k, full.get(k))
        }
        return out
    }

    fun toPatchFor(keys: Set<String>): JSONObject = JSONObject().put("GENERAL", toJsonFor(keys))

    companion object {
        val TIME_REGEX = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")

        fun fromJson(o: JSONObject?): GeneralSettings {
            if (o == null) return GeneralSettings()
            return GeneralSettings(
                redLimitEnabled = o.optBoolean("red_limit_enabled", false),
                redLimitValue = o.optInt("red_limit_value", 255).coerceIn(0, 255),
                autoOnEnabled = o.optBoolean("auto_turn_on_enabled", false),
                autoOnTime = o.optString("auto_turn_on_time", "18:00"),
                autoOffEnabled = o.optBoolean("auto_turn_off_enabled", false),
                autoOffTime = o.optString("auto_turn_off_time", "23:00"),
                shortBlinkCount = o.optInt("short_blink_count", 3).coerceIn(0, 20),
                shortBlinkSpeed = o.optInt("short_blink_speed", 200).coerceIn(50, 5000),
                shortAlertBrightness = o.optInt("short_alert_brightness", 128).coerceIn(0, 255)
            )
        }

        fun fromSettingsRoot(root: JSONObject?): GeneralSettings =
            fromJson(root?.optJSONObject("GENERAL"))
    }
}

/** Per-mode working state used by the editors; [toJson] matches the MODE_n schemas. */
sealed interface ModeEditorState {

    /** OFF has no parameters — placeholder so `defaultFor` stays exhaustive. */
    data object Off : ModeEditorState {
        override fun toJson(): JSONObject = JSONObject().put("name", "OFF")
    }

    data class Solid(val corners: List<Rgb> = Rgb.DEFAULT_CORNERS) : ModeEditorState {
        override fun toJson(): JSONObject = JSONObject()
            .put("name", "SOLID")
            .put("colors", Rgb.listToJson(corners))
    }

    data class Blink(
        val interval: Int = 500,
        val noOff: Boolean = false,
        val colors: List<Rgb> = Rgb.DEFAULT_PALETTE
    ) : ModeEditorState {
        override fun toJson(): JSONObject = JSONObject()
            .put("name", "BLINK")
            .put("interval", interval)
            .put("no_off", noOff)
            .put("colors", Rgb.listToJson(colors))
    }

    data class Wave(
        val interval: Int = 50,
        val colors: List<Rgb> = Rgb.DEFAULT_PALETTE
    ) : ModeEditorState {
        override fun toJson(): JSONObject = JSONObject()
            .put("name", "WAVE")
            .put("interval", interval)
            .put("pattern", "diagonal")
            .put("colors", Rgb.listToJson(colors))
    }

    data class Glow(
        val interval: Int = 30,
        val smoothTransitions: Boolean = false,
        val breathingEffect: Boolean = true,
        val colors: List<Rgb> = Rgb.DEFAULT_PALETTE
    ) : ModeEditorState {
        override fun toJson(): JSONObject = JSONObject()
            .put("name", "GLOW")
            .put("interval", interval)
            .put("smooth_transitions", smoothTransitions)
            .put("breathing_effect", breathingEffect)
            .put("colors", Rgb.listToJson(colors))
    }

    data class Trail(
        val speed: Int = 1000,
        val direction: Boolean = true,
        val trailLength: Int = 3,
        val brightness: Int = 100,
        val colors: List<Rgb> = Rgb.DEFAULT_PALETTE
    ) : ModeEditorState {
        override fun toJson(): JSONObject = JSONObject()
            .put("name", "TRAIL")
            .put("speed", speed)
            .put("direction", direction)
            .put("trail_length", trailLength)
            .put("brightness", brightness)
            .put("colors", Rgb.listToJson(colors))
    }

    data class Spiral(
        val speed: Int = 1000,
        val direction: Boolean = true,
        val brightness: Int = 100,
        val paused: Boolean = false,
        val colors: List<Rgb> = Rgb.DEFAULT_CORNERS
    ) : ModeEditorState {
        override fun toJson(): JSONObject = JSONObject()
            .put("name", "SPIRAL")
            .put("speed", speed)
            .put("direction", direction)
            .put("brightness", brightness)
            .put("paused", paused)
            .put("colors", Rgb.listToJson(colors))
    }

    fun toJson(): JSONObject

    companion object {
        fun defaultFor(mode: Int): ModeEditorState = when (mode) {
            1 -> Solid()
            2 -> Blink()
            3 -> Wave()
            4 -> Glow()
            5 -> Trail()
            6 -> Spiral()
            else -> Off
        }

        /** Parse a MODE_n JSON into the typed editor state (tolerates missing keys). */
        fun fromJson(mode: Int, json: String): ModeEditorState {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return defaultFor(mode)
            val colors = Rgb.listFromJson(o.optJSONArray("colors"), Rgb.DEFAULT_PALETTE)
            return when (mode) {
                1 -> Solid(Rgb.listFromJson(o.optJSONArray("colors"), Rgb.DEFAULT_CORNERS).let {
                    // SOLID always has exactly 4 corners
                    (it + Rgb.DEFAULT_CORNERS).take(4)
                })
                2 -> Blink(
                    interval = o.optInt("interval", 500).coerceIn(50, 10_000),
                    noOff = o.optBoolean("no_off", false),
                    colors = colors
                )
                3 -> Wave(
                    interval = o.optInt("interval", 50).coerceIn(10, 2000),
                    colors = colors
                )
                4 -> Glow(
                    interval = o.optInt("interval", 30).coerceIn(10, 1000),
                    smoothTransitions = o.optBoolean("smooth_transitions", false),
                    breathingEffect = o.optBoolean("breathing_effect", true),
                    colors = colors
                )
                5 -> Trail(
                    speed = o.optInt("speed", 1000).coerceIn(100, 20_000),
                    direction = o.optBoolean("direction", true),
                    trailLength = o.optInt("trail_length", 3).coerceIn(1, 4),
                    brightness = o.optInt("brightness", 100).coerceIn(0, 100),
                    colors = colors
                )
                6 -> Spiral(
                    speed = o.optInt("speed", 1000).coerceIn(50, 20_000),
                    direction = o.optBoolean("direction", true),
                    brightness = o.optInt("brightness", 100).coerceIn(0, 100),
                    paused = o.optBoolean("paused", false),
                    colors = Rgb.listFromJson(o.optJSONArray("colors"), Rgb.DEFAULT_CORNERS).let {
                        (it + Rgb.DEFAULT_CORNERS).take(4)
                    }
                )
                else -> defaultFor(mode)
            }
        }
    }
}
