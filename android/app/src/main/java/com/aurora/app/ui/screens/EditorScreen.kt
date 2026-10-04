package com.aurora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aurora.app.ble.ModeEditorState
import com.aurora.app.ble.Rgb
import com.aurora.app.model.Mode
import com.aurora.app.ui.components.ColorSliders
import com.aurora.app.ui.components.LabeledSlider
import com.aurora.app.ui.components.PaletteEditor
import com.aurora.app.ui.components.SectionCard
import com.aurora.app.viewmodel.AuroraViewModel

private val CORNER_LABELS = arrayOf("TL", "TR", "BL", "BR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AuroraViewModel, modeId: Int, modifier: Modifier = Modifier) {
    val mode = Mode.fromId(modeId)
    val state by vm.editorState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("${mode.label} editor") },
                navigationIcon = {
                    IconButton(onClick = { vm.backFromEditor() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.runEditor() },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("RUN")
                }
                Button(
                    onClick = { vm.saveEditor() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled = state != null
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("SAVE")
                }
            }
        }
    ) { padding ->
        when (val s = state) {
            null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    "Loading ${mode.label} settings…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                when {
                    s is ModeEditorState.Solid -> SolidEditor(s, vm)
                    s is ModeEditorState.Blink -> BlinkEditor(s) { vm.patchEditor(it) }
                    s is ModeEditorState.Wave -> WaveEditor(s) { vm.patchEditor(it) }
                    s is ModeEditorState.Glow -> GlowEditor(s) { vm.patchEditor(it) }
                    s is ModeEditorState.Trail -> TrailEditor(s) { vm.patchEditor(it) }
                    s is ModeEditorState.Spiral -> SpiralEditor(s) { vm.patchEditor(it) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// --------------------------------------------------------------------- SOLID

@Composable
private fun SolidEditor(state: ModeEditorState.Solid, vm: AuroraViewModel) {
    var selectedCorner = remember { mutableIntStateOf(0) }
    val corner = selectedCorner.value.coerceIn(0, 3)

    SectionCard(title = "CORNER PREVIEW") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            state.corners.forEachIndexed { i, c ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selectedCorner.value = i }
                        .padding(vertical = 10.dp)
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(c.r, c.g, c.b))
                            .border(
                                width = if (i == corner) 3.dp else 1.dp,
                                color = if (i == corner) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        CORNER_LABELS[i],
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i == corner) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    SectionCard(title = "EDIT ${CORNER_LABELS[corner]} COLOR") {
        ColorSliders(state.corners[corner], onChange = { newColor ->
            vm.solidCornerChanged(corner, newColor)
        })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val current = state.corners[corner]
            OutlinedButton(
                onClick = { vm.solidAllCorners(current) },
                modifier = Modifier.weight(1f)
            ) { Text("All = ${current.toHex()}") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Colors stream to the box live · SAVE stores them",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --------------------------------------------------------------------- BLINK

@Composable
private fun BlinkEditor(state: ModeEditorState.Blink, onPatch: (ModeEditorState.Blink) -> Unit) {
    SectionCard(title = "TIMING") {
        LabeledSlider("Interval", state.interval, { onPatch(state.copy(interval = it)) }, 50..2000, " ms")
        SwitchRowInline("No off phase", state.noOff, "Cycle colors without going dark") {
            onPatch(state.copy(noOff = it))
        }
    }
    SectionCard(title = "COLORS (${state.colors.size}/8)") {
        PaletteEditor(state.colors, { onPatch(state.copy(colors = it)) }, max = 8)
    }
}

// ---------------------------------------------------------------------- WAVE

@Composable
private fun WaveEditor(state: ModeEditorState.Wave, onPatch: (ModeEditorState.Wave) -> Unit) {
    SectionCard(title = "TIMING") {
        LabeledSlider("Step interval", state.interval, { onPatch(state.copy(interval = it)) }, 10..1000, " ms")
        Text(
            "Diagonal corner groups fade against each other.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SectionCard(title = "PALETTE (${state.colors.size}/8)") {
        // firmware clamps wave palettes to ≥2 (ModeWave indexes arr[1])
        PaletteEditor(state.colors, { onPatch(state.copy(colors = it)) }, max = 8, min = 2)
    }
}

// ---------------------------------------------------------------------- GLOW

@Composable
private fun GlowEditor(state: ModeEditorState.Glow, onPatch: (ModeEditorState.Glow) -> Unit) {
    SectionCard(title = "BEHAVIOR") {
        LabeledSlider("Step interval", state.interval, { onPatch(state.copy(interval = it)) }, 10..500, " ms")
        SwitchRowInline("Breathing", state.breathingEffect, "Slow brightness pulse") {
            onPatch(state.copy(breathingEffect = it))
        }
        SwitchRowInline("Smooth color fades", state.smoothTransitions) {
            onPatch(state.copy(smoothTransitions = it))
        }
    }
    SectionCard(title = "COLORS (${state.colors.size}/8)") {
        PaletteEditor(state.colors, { onPatch(state.copy(colors = it)) }, max = 8, min = 2)
    }
}

// --------------------------------------------------------------------- TRAIL

@Composable
private fun TrailEditor(state: ModeEditorState.Trail, onPatch: (ModeEditorState.Trail) -> Unit) {
    SectionCard(title = "MOTION") {
        LabeledSlider("Speed (ms per lap)", state.speed, { onPatch(state.copy(speed = it)) }, 100..10_000, " ms")
        LabeledSlider("Trail length", state.trailLength, { onPatch(state.copy(trailLength = it)) }, 1..4)
        LabeledSlider("Brightness", state.brightness, { onPatch(state.copy(brightness = it)) }, 0..100, "%")
        SwitchRowInline("Forward direction", state.direction) { onPatch(state.copy(direction = it)) }
    }
    SectionCard(title = "COLORS (${state.colors.size}/8)") {
        PaletteEditor(state.colors, { onPatch(state.copy(colors = it)) }, max = 8)
    }
}

// -------------------------------------------------------------------- SPIRAL

@Composable
private fun SpiralEditor(state: ModeEditorState.Spiral, onPatch: (ModeEditorState.Spiral) -> Unit) {
    SectionCard(title = "MOTION") {
        LabeledSlider("Speed (ms per frame)", state.speed, { onPatch(state.copy(speed = it)) }, 50..10_000, " ms")
        LabeledSlider("Brightness", state.brightness, { onPatch(state.copy(brightness = it)) }, 0..100, "%")
        SwitchRowInline("Forward direction", state.direction) { onPatch(state.copy(direction = it)) }
        SwitchRowInline("Paused", state.paused, "Freeze the animation") { onPatch(state.copy(paused = it)) }
    }
    SectionCard(title = "COLORS (exactly 4)") {
        PaletteEditor(state.colors, { onPatch(state.copy(colors = it)) }, max = 4, fixed = true)
    }
}

// --------------------------------------------------------------------- shared

@Composable
private fun SwitchRowInline(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
