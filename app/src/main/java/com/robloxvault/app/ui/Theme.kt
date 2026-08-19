package com.robloxvault.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF4F46E5)
private val IndigoDark = Color(0xFF6366F1)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Color(0xFF0EA5E9),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0B1020),
    surface = Color(0xFF111827),
)

@Composable
fun RobloxVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
