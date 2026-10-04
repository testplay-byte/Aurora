package com.aurora.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Aurora ships dark-only: one carefully-checked scheme beats two half-tuned ones.
// Status/nav bar colors come from themes.xml (+ edge-to-edge insets handled by Scaffold).
private val AuroraDarkColors = darkColorScheme(
    primary = AuroraTeal,
    onPrimary = Color(0xFF04231B),
    primaryContainer = AuroraTealDim,
    onPrimaryContainer = AuroraOnTealDim,
    secondary = AuroraSky,
    onSecondary = Color(0xFF04202B),
    tertiary = AuroraCoral,
    onTertiary = Color(0xFF2B0A0A),
    background = AuroraBg,
    onBackground = AuroraText,
    surface = AuroraSurface,
    onSurface = AuroraText,
    surfaceVariant = AuroraSurfaceHigh,
    onSurfaceVariant = AuroraTextDim,
    surfaceTint = AuroraTeal,
    outline = AuroraOutline,
    outlineVariant = AuroraOutline,
    error = AuroraCoral,
    onError = Color(0xFF2B0A0A)
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroraDarkColors,
        typography = AuroraTypography,
        content = content
    )
}
