package com.aurora.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.aurora.app.ble.BleProtocol

/** The 7 firmware modes — ids and labels match BLE-PROTOCOL.md. */
enum class Mode(val id: Int, val label: String, val icon: ImageVector) {
    OFF(0, "OFF", Icons.Filled.PowerSettingsNew),
    SOLID(1, "SOLID", Icons.Filled.ColorLens),
    BLINK(2, "BLINK", Icons.Filled.FlashOn),
    WAVE(3, "WAVE", Icons.Filled.Waves),
    GLOW(4, "GLOW", Icons.Filled.WbSunny),
    TRAIL(5, "TRAIL", Icons.Filled.Timeline),
    SPIRAL(6, "SPIRAL", Icons.Filled.Autorenew);

    /** Modes with an editor (everything except OFF). */
    val editable: Boolean get() = id != OFF.id

    companion object {
        fun fromId(id: Int): Mode = entries.firstOrNull { it.id == id } ?: OFF
        fun selectable(): List<Mode> = entries.filter { it.id != OFF.id }
        fun byName(name: String): Mode? =
            entries.firstOrNull { it.label.equals(name, ignoreCase = true) }

        /** Protocol sanity — kept so a rename can't silently drift from the firmware. */
        init {
            require(entries.size == BleProtocol.MODE_COUNT) {
                "Mode count mismatch: app=${entries.size} firmware=${BleProtocol.MODE_COUNT}"
            }
        }
    }
}
