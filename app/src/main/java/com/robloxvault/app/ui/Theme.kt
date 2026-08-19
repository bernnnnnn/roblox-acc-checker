package com.robloxvault.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- "Noctra" Discord theme palette --------------------------------------
val NoctraBlack = Color(0xFF000000)       // BACKGROUND_PRIMARY
val NoctraSurface = Color(0xFF0E0F12)      // BACKGROUND_SECONDARY (cards)
val NoctraSurfaceHi = Color(0xFF15181D)    // BACKGROUND_TERTIARY
val NoctraFloating = Color(0xFF111317)     // BACKGROUND_FLOATING (header)
val NoctraOutline = Color(0xFF202733)      // BACKGROUND_MODIFIER_ACCENT
val NoctraSelected = Color(0xFF313C49)     // BACKGROUND_MODIFIER_SELECTED
val NoctraAccent = Color(0xFFD8E8FF)       // TEXT_LINK / brand accent (soft blue)
val NoctraTextHi = Color(0xFFFFFFFF)       // HEADER_PRIMARY
val NoctraText = Color(0xFFF4F6F8)         // TEXT_NORMAL
val NoctraMuted = Color(0xFF7A8796)        // TEXT_MUTED
val NoctraChip = Color(0xFF3C4856)         // BACKGROUND_ACCENT

// Functional status colors, tuned to sit on the black theme.
val StatusGood = Color(0xFF3BA55D)
val StatusBad = Color(0xFFED4245)
val StatusWarn = Color(0xFFE0A030)

private val NoctraColors = darkColorScheme(
    primary = NoctraAccent,
    onPrimary = NoctraBlack,
    primaryContainer = NoctraSelected,
    onPrimaryContainer = NoctraTextHi,
    secondary = NoctraChip,
    onSecondary = NoctraTextHi,
    background = NoctraBlack,
    onBackground = NoctraText,
    surface = NoctraSurface,
    onSurface = NoctraText,
    surfaceVariant = NoctraSurfaceHi,
    onSurfaceVariant = NoctraMuted,
    outline = NoctraOutline,
    error = StatusBad,
    onError = NoctraTextHi,
)

@Composable
fun RobloxVaultTheme(content: @Composable () -> Unit) {
    // The Noctra theme is inherently dark — always use it.
    MaterialTheme(colorScheme = NoctraColors, content = content)
}
