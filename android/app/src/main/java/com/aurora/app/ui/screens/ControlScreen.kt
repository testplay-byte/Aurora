package com.aurora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aurora.app.ble.BleManager
import com.aurora.app.model.Mode
import com.aurora.app.ui.components.ModeCard
import com.aurora.app.ui.components.SectionCard
import com.aurora.app.viewmodel.AuroraViewModel
import com.aurora.app.viewmodel.AuroraViewModel.Tab

@Composable
fun ControlScreen(vm: AuroraViewModel, modifier: Modifier = Modifier) {
    val currentMode by vm.currentMode.collectAsState()
    val latency by vm.latencyMs.collectAsState()
    val rssi by vm.ble.rssi.collectAsState()
    val connState by vm.ble.state.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ---- connection status ------------------------------------------------
        SectionCard(title = "CONNECTION") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    val name = (connState as? BleManager.ConnectionState.Connected)?.name
                        ?: "Not connected"
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            rssi?.let { "$it dBm" } ?: "— dBm",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            latency?.let { "$it ms" } ?: "— ms",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(onClick = { vm.ping() }) { Text("PING") }
                IconButton(onClick = { vm.refreshMode() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh mode")
                }
            }
            Spacer(Modifier.height(6.dp))
            val modeLabel = if (currentMode >= 0) Mode.fromId(currentMode).label else "…"
            Text(
                "Active mode: $modeLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ---- STOP -------------------------------------------------------------
        Button(
            onClick = { vm.switchMode(Mode.OFF.id) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("STOP", style = MaterialTheme.typography.titleMedium)
        }

        // ---- mode grid --------------------------------------------------------
        SectionCard(title = "MODES") {
            val modes = Mode.selectable()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modes.chunked(2).forEach { rowModes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowModes.forEach { mode ->
                            ModeCard(
                                mode = mode,
                                active = currentMode == mode.id,
                                onClick = { vm.switchMode(mode.id) },
                                modifier = Modifier.weight(1f),
                                trailingIcon = if (currentMode == mode.id) Icons.Filled.CheckCircle else null
                            )
                        }
                        if (rowModes.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- shortcuts --------------------------------------------------------
        SectionCard(title = "SHORTCUTS") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.openTab(Tab.MODES) },
                    modifier = Modifier.weight(1f)
                ) { Text("Edit modes") }
                OutlinedButton(
                    onClick = { vm.disconnect() },
                    modifier = Modifier.weight(1f)
                ) { Text("Disconnect") }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
