package com.robloxvault.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand accents (also used for the header gradient).
val BrandIndigo = Color(0xFF4F46E5)
val BrandViolet = Color(0xFF7C3AED)
val BrandCyan = Color(0xFF06B6D4)

private val LightColors = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = BrandCyan,
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF13151F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF13151F),
    surfaceVariant = Color(0xFFEEF0F7),
    onSurfaceVariant = Color(0xFF515667),
    outline = Color(0xFFD3D7E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B87FF),
    onPrimary = Color(0xFF15132E),
    primaryContainer = Color(0xFF2A2652),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0A0D1A),
    onBackground = Color(0xFFE7E9F2),
    surface = Color(0xFF12151F),
    onSurface = Color(0xFFE7E9F2),
    surfaceVariant = Color(0xFF1C2030),
    onSurfaceVariant = Color(0xFFA6ABBE),
    outline = Color(0xFF2C3242),
)

@Composable
fun RobloxVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
