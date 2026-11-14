package com.bharatbussim.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HudBgDark = Color(0xFF0C1018)
val HudPanelSurface = Color(0xE6161D29)
val HudAmber = Color(0xFFF59E0B)
val HudAmberBright = Color(0xFFFBBF24)
val HudCyan = Color(0xFF38BDF8)
val HudEmerald = Color(0xFF34D399)
val HudRed = Color(0xFFEF4444)
val HudTextPrimary = Color(0xFFF8FAFC)
val HudTextMuted = Color(0xFF94A3B8)

private val SimColorScheme = darkColorScheme(
    primary = HudAmber,
    onPrimary = Color(0xFF0F172A),
    secondary = HudCyan,
    onSecondary = Color(0xFF0F172A),
    tertiary = HudEmerald,
    background = HudBgDark,
    surface = HudPanelSurface,
    onBackground = HudTextPrimary,
    onSurface = HudTextPrimary,
    error = HudRed
)

@Composable
fun BharatBusSimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SimColorScheme,
        content = content
    )
}
