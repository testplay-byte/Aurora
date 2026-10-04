package com.aurora.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.aurora.app.ble.Rgb
import com.aurora.app.model.Mode

/** Dark card with a small caps title — the app's section container. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing
                )
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Label + current value on one row, slider below. */
@Composable
fun LabeledSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    unit: String = "",
    valueLabel: String = value.toString() + unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled
        )
    }
}

/** Title + optional subtitle with a trailing switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** RGB sliders + hex readout for one color. */
@Composable
fun ColorSliders(
    color: Rgb,
    onChange: (Rgb) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(color.r, color.g, color.b))
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(10.dp))
                )
            Spacer(Modifier.width(12.dp))
            Text(
                color.toHex(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        LabeledSlider("Red", color.r, { onChange(color.copy(r = it)) }, 0..255)
        LabeledSlider("Green", color.g, { onChange(color.copy(g = it)) }, 0..255)
        LabeledSlider("Blue", color.b, { onChange(color.copy(b = it)) }, 0..255)
    }
}

/**
 * Palette editor: swatch chips (tap = select, edit via [ColorSliders] below),
 * add/remove up to [max] colors. [fixed] pins the palette (SPIRAL = exactly 4).
 */
@Composable
fun PaletteEditor(
    colors: List<Rgb>,
    onChange: (List<Rgb>) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 8,
    min: Int = 1,
    fixed: Boolean = false
) {
    if (colors.isEmpty()) return // BEFORE coerceIn — empty list would throw
    var selected by remember { mutableIntStateOf(0) }
    val safeSelected = selected.coerceIn(0, colors.lastIndex)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEachIndexed { i, c ->
                val isSelected = i == safeSelected
                Box(
                    Modifier
                        .size(if (isSelected) 40.dp else 34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(c.r, c.g, c.b))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            // onSurfaceVariant ≈ 6:1 on surface — outline was ≈1.4:1
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selected = i }
                )
            }
            Spacer(Modifier.weight(1f))
            if (!fixed && colors.size > min) {
                IconButton(onClick = {
                    val idx = safeSelected
                    onChange(colors.filterIndexed { i, _ -> i != idx })
                    selected = (idx - 1).coerceAtLeast(0)
                }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Remove color")
                }
            }
            if (!fixed && colors.size < max) {
                IconButton(onClick = { onChange(colors + colors[safeSelected]) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add color")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ColorSliders(colors[safeSelected], onChange = { newColor ->
            onChange(colors.toMutableList().also { it[safeSelected] = newColor })
        })
    }
}

/** Mode tile for the dashboard / mode picker grids. */
@Composable
fun ModeCard(
    mode: Mode,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (active) 2.dp else 1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(vertical = 18.dp, horizontal = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = mode.label,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                mode.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            if (trailingIcon != null) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    trailingIcon,
                    contentDescription = "active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
