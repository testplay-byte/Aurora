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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.app.model.Mode
import com.aurora.app.ui.components.ModeCard
import com.aurora.app.ui.components.SectionCard
import com.aurora.app.viewmodel.AuroraViewModel

@Composable
fun ModesScreen(vm: AuroraViewModel, modifier: Modifier = Modifier) {
    val currentMode by vm.currentMode.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SectionCard(title = "EDIT MODE SETTINGS") {
            Text(
                "Changes stream to the box live; SAVE persists them to device storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            val modes = Mode.selectable()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modes.chunked(2).forEach { rowModes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowModes.forEach { mode ->
                            ModeCard(
                                mode = mode,
                                active = currentMode == mode.id,
                                onClick = { vm.openEditor(mode.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowModes.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        SectionCard(title = "TIP") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tap a mode to open its editor",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
