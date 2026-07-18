package com.android.smarthome.gateway.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SmartHomeColors {
    val Background = Color(0xFF1B1917)
    val BackgroundSoft = Color(0xFF25221F)
    val Card = Color(0xFF2B2825)
    val CardAlt = Color(0xFF332F2B)
    val Stroke = Color(0xFF3A3632)
    val Accent = Color(0xFFE3A857)
    val AccentDark = Color(0xFFB97F32)
    val AccentSoft = Color(0xFF4A3B26)
    val Success = Color(0xFF7CC77A)
    val Danger = Color(0xFFE56B5D)
    val Info = Color(0xFF6FA8DC)
    val TextPrimary = Color(0xFFF5F1EC)
    val TextSecondary = Color(0xFFB9B2A9)
    val Disabled = Color(0xFF726C64)
}

private val colors = darkColorScheme(
    primary = SmartHomeColors.Accent,
    onPrimary = SmartHomeColors.Background,
    secondary = SmartHomeColors.AccentSoft,
    onSecondary = SmartHomeColors.TextPrimary,
    background = SmartHomeColors.Background,
    onBackground = SmartHomeColors.TextPrimary,
    surface = SmartHomeColors.Card,
    onSurface = SmartHomeColors.TextPrimary,
    surfaceVariant = SmartHomeColors.CardAlt,
    onSurfaceVariant = SmartHomeColors.TextSecondary,
    error = SmartHomeColors.Danger
)

@Composable
fun SmartHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
