package com.example.smart_home_mobile_app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun TuyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Color(0xFFF6F1EA),
            surface = Color(0xF2FFFDFC),
            surfaceVariant = Color(0xFFECE3D8),
            primary = Color(0xFF396A65),
            secondary = Color(0xFFB46F45),
            onBackground = Color(0xFF2E2925),
            onSurface = Color(0xFF2E2925),
            onSurfaceVariant = Color(0xFF746B62),
            error = Color(0xFFB3261E),
        )
    ) {
        content()
    }
}
