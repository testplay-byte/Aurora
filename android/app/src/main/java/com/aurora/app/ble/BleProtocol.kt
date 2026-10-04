package com.aurora.app.ble

/**
 * Pure protocol layer for the Aurora BLE command channel.
 * Mirrors docs/BLE-PROTOCOL.md — ASCII, colon fields, newline-terminated.
 * Everything here is unit-testable with zero Android dependencies.
 */
object BleProtocol {

    const val SERVICE_UUID = "00004faf-0000-1000-8000-00805f9b34fb"
    const val CHAR_WRITE_UUID = "0000beb5-0000-1000-8000-00805f9b34fb"
    const val CHAR_NOTIFY_UUID = "0000beb6-0000-1000-8000-00805f9b34fb"
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    /** Advertised-name filter (v2 firmware; v1 used "NTLWC-LED"). */
    const val DEVICE_NAME_FILTER = "Aurora"

    const val MODE_COUNT = 7
    val MODE_NAMES = arrayOf("OFF", "SOLID", "BLINK", "WAVE", "GLOW", "TRAIL", "SPIRAL")

    /** Commands (Phone → ESP32), each newline-terminated on the wire. */
    fun getMode() = "GET:MODE"
    fun setMode(mode: Int) = "SET:MODE:$mode"
    fun off() = "OFF"
    fun led(corner: Int, r: Int, g: Int, b: Int) = "LED:$corner:$r,$g,$b"
    fun all(r: Int, g: Int, b: Int) = "ALL:$r,$g,$b"
    fun save() = "SAVE"
    fun status() = "STATUS"
    fun ping() = "PING"
    fun getSettings() = "GETSETTINGS"
    fun setSettings(json: String) = "SETSETTINGS:$json"
    fun getModeSettings(mode: Int) = "GET:MODESETTINGS:$mode"
    fun setModeSettings(mode: Int, json: String) = "SET:MODESETTINGS:$mode:$json"

    /** Wire encoding: command + newline, ASCII/UTF-8 bytes. */
    fun encode(command: String): ByteArray = (command + "\n").toByteArray(Charsets.UTF_8)

    /**
     * Response-line extractor. The firmware sends ONE complete response per
     * notification with NO trailing newline (Ble.cpp bleRespond), so a payload
     * without '\n' is delivered whole; payloads containing newlines are split.
     */
    class LineBuffer {
        fun feed(chunk: String): List<String> {
            if (!chunk.contains('\n')) {
                val single = chunk.trim()
                return if (single.isEmpty()) emptyList() else listOf(single)
            }
            return chunk.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        }

        fun reset() { /* no cross-packet state — kept for API symmetry */ }
    }

    sealed interface Response {
        /** `MODE:<n>:<NAME>` / `STATUS:<n>:<NAME>` */
        data class ModeValue(val mode: Int, val name: String) : Response
        /** `OK:<anything>` — [detail] is the text after `OK:` */
        data class Ok(val detail: String) : Response
        /** `ERR:<CODE>` */
        data class Error(val code: String) : Response
        /** `SETTINGS:<json>` */
        data class Settings(val json: String) : Response
        /** `MODESETTINGS:<n>:<json>` */
        data class ModeSettings(val mode: Int, val json: String) : Response
        data object Pong : Response
        data class Unknown(val raw: String) : Response
    }

    fun parse(line: String): Response {
        val trimmed = line.trim()
        return when {
            trimmed == "PONG" -> Response.Pong
            trimmed.startsWith("ERR:") -> Response.Error(trimmed.substring(4))
            trimmed.startsWith("OK:") -> Response.Ok(trimmed.substring(3))
            trimmed.startsWith("SETTINGS:") -> Response.Settings(trimmed.substring(9))
            trimmed.startsWith("MODESETTINGS:") -> {
                val rest = trimmed.substring(13)
                val sep = rest.indexOf(':')
                if (sep <= 0) Response.Unknown(trimmed)
                else {
                    val mode = rest.substring(0, sep).toIntOrNull()
                    if (mode == null) Response.Unknown(trimmed)
                    else Response.ModeSettings(mode, rest.substring(sep + 1))
                }
            }
            trimmed.startsWith("MODE:") -> parseModeValue(trimmed.substring(5), trimmed)
            trimmed.startsWith("STATUS:") -> parseModeValue(trimmed.substring(7), trimmed)
            else -> Response.Unknown(trimmed)
        }
    }

    private fun parseModeValue(rest: String, raw: String): Response {
        val sep = rest.indexOf(':')
        val mode = if (sep > 0) rest.substring(0, sep).toIntOrNull() else null
        return if (mode != null && mode in 0 until MODE_COUNT && sep + 1 < rest.length)
            Response.ModeValue(mode, rest.substring(sep + 1))
        else
            Response.Unknown(raw)
    }

    /** True if this firmware device advertised under the current name filter. */
    fun matchesDeviceName(name: String?): Boolean =
        name != null && name.contains(DEVICE_NAME_FILTER, ignoreCase = true)
}
