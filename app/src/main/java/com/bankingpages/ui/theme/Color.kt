package com.bankingpages.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Warm off-white surfaces with a hot terracotta accent that runs as a gradient on every primary control.
val Clay = Color(0xFFD9542B)
val ClayPressed = Color(0xFFB8421F)
val CreamBackground = Color(0xFFF6F4F0)
val CreamSurface = Color(0xFFFFFFFF)
val CreamRaised = Color(0xFFF0ECE5)
val CreamOutline = Color(0xFFE4DFD5)
val InkPrimary = Color(0xFF1B1A19)
val InkSecondary = Color(0xFF6B6760)

// Deep, low-glare dark tones rather than pure black.
val ClayDark = Color(0xFFF08259)
val ClayDarkPressed = Color(0xFFD96C45)
val WarmBackground = Color(0xFF121110)
val WarmSurface = Color(0xFF1D1C1A)
val WarmRaised = Color(0xFF2A2826)
val WarmOutline = Color(0xFF38352F)
val ParchmentPrimary = Color(0xFFF5F4EF)
val ParchmentSecondary = Color(0xFFA9A59B)

/** The one gradient behind every primary button, the add pills and the selected tab. */
val AccentStart = Color(0xFFF58A55)
val AccentEnd = Color(0xFFD13F2B)
val AccentBrush = Brush.linearGradient(listOf(AccentStart, AccentEnd))

// Status colours, one pair per mode so contrast holds either way.
val SuccessLight = Color(0xFF1B7334)
val SuccessDark = Color(0xFF7FC98D)
val SuccessSurfaceLight = Color(0xFFECF5ED)
val SuccessSurfaceDark = Color(0xFF233026)

val WarningLight = Color(0xFF9A6212)
val WarningDark = Color(0xFFE0B054)

val DangerLight = Color(0xFFC0362A)
val DangerDark = Color(0xFFEF8A7C)
