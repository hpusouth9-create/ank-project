package com.p2pvoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Color Palette — Deep space dark with electric teal accents ────────────
val SpaceBlack    = Color(0xFF0A0E1A)
val DeepNavy      = Color(0xFF0F1629)
val CardNavy      = Color(0xFF151C35)
val BorderNavy    = Color(0xFF1E2A4A)
val ElectricTeal  = Color(0xFF00E5CC)
val TealDim       = Color(0xFF00B8A3)
val PulseGreen    = Color(0xFF00FF88)
val AlertRed      = Color(0xFFFF4757)
val AlertRedDim   = Color(0xFFCC3344)
val TextPrimary   = Color(0xFFE8EDF5)
val TextSecondary = Color(0xFF8895B3)
val TextDim       = Color(0xFF4A5580)

private val DarkColors = darkColorScheme(
    primary          = ElectricTeal,
    onPrimary        = SpaceBlack,
    secondary        = PulseGreen,
    onSecondary      = SpaceBlack,
    error            = AlertRed,
    onError          = TextPrimary,
    background       = SpaceBlack,
    onBackground     = TextPrimary,
    surface          = CardNavy,
    onSurface        = TextPrimary,
    surfaceVariant   = DeepNavy,
    onSurfaceVariant = TextSecondary,
    outline          = BorderNavy
)

@Composable
fun P2PVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
