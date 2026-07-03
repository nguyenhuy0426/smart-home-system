package com.example.smart_home_mobile_app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun TuyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Color(0xFFF0F4F8), // Soft premium grey-blue background
            surface = Color.White,
            primary = Color(0xFF1E88E5),    // LG ThinQ / Tuya style blue
            secondary = Color(0xFF43A047),
            onBackground = Color(0xFF1C1C1E)
        )
    ) {
        content()
    }
}
