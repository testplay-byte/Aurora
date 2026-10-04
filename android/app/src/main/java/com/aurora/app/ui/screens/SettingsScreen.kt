package com.aurora.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.app.ble.GeneralSettings
import com.aurora.app.ui.components.LabeledSlider
import com.aurora.app.ui.components.SectionCard
import com.aurora.app.ui.components.SwitchRow
import com.aurora.app.viewmodel.AuroraViewModel

@Composable
fun SettingsScreen(vm: AuroraViewModel, modifier: Modifier = Modifier) {
    val general by vm.general.collectAsState()
    val loaded by vm.generalLoaded.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        if (!loaded) {
            SectionCard(title = "SAFE MODE") {
                Text(
                    "Device settings couldn't be read in one piece — showing defaults. " +
                        "Saving only writes the fields you actually change, so nothing on the box is clobbered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // ---------------------------------------------------------- red limit
        SectionCard(title = "RED LED LIMIT") {
            Text(
                "Caps the red channel globally (heat / power protection).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                title = "Limit red channel",
                checked = general.redLimitEnabled,
                onCheckedChange = { vm.updateGeneral(general.copy(redLimitEnabled = it), "red_limit_enabled") }
            )
            LabeledSlider(
                label = "Max red",
                value = general.redLimitValue,
                onValueChange = { vm.updateGeneral(general.copy(redLimitValue = it), "red_limit_value") },
                range = 0..255,
                enabled = general.redLimitEnabled
            )
        }

        // ---------------------------------------------------------- schedules
        SectionCard(title = "AUTO ON / OFF") {
            Text(
                "Needs device time (NTP over Wi-Fi) — schedules stay idle until synced.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Auto turn ON",
                checked = general.autoOnEnabled,
                onCheckedChange = { vm.updateGeneral(general.copy(autoOnEnabled = it), "auto_turn_on_enabled") }
            )
            TimeField(
                value = general.autoOnTime,
                onValueChange = { vm.updateGeneral(general.copy(autoOnTime = it), "auto_turn_on_time") },
                enabled = general.autoOnEnabled
            )
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                title = "Auto turn OFF",
                checked = general.autoOffEnabled,
                onCheckedChange = { vm.updateGeneral(general.copy(autoOffEnabled = it), "auto_turn_off_enabled") }
            )
            TimeField(
                value = general.autoOffTime,
                onValueChange = { vm.updateGeneral(general.copy(autoOffTime = it), "auto_turn_off_time") },
                enabled = general.autoOffEnabled
            )
        }

        // ------------------------------------------------------- short alert
        SectionCard(title = "SHORT-CIRCUIT ALERT") {
            Text(
                "Blinks red when the protection pin trips.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LabeledSlider(
                label = "Blink count",
                value = general.shortBlinkCount,
                onValueChange = { vm.updateGeneral(general.copy(shortBlinkCount = it), "short_blink_count") },
                range = 0..20
            )
            LabeledSlider(
                label = "Blink speed",
                value = general.shortBlinkSpeed,
                onValueChange = { vm.updateGeneral(general.copy(shortBlinkSpeed = it), "short_blink_speed") },
                range = 50..2000,
                unit = " ms"
            )
            LabeledSlider(
                label = "Alert brightness",
                value = general.shortAlertBrightness,
                onValueChange = { vm.updateGeneral(general.copy(shortAlertBrightness = it), "short_alert_brightness") },
                range = 0..255
            )
        }

        Button(
            onClick = { vm.saveGeneral() },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Save settings to device") }

        Text(
            "Wi-Fi network settings live in the Web UI, not here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TimeField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val valid = GeneralSettings.TIME_REGEX.matches(value)
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.length <= 5) onValueChange(s) },
        enabled = enabled,
        singleLine = true,
        label = { Text("Time (HH:MM)") },
        isError = enabled && !valid,
        supportingText = if (enabled && !valid) {
            { Text("Format: 18:00") }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
