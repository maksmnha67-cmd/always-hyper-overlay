package com.shyz.alwayshyper.ui.theme

import androidx.compose.ui.graphics.Color
import com.shyz.alwayshyper.ThemeColor

// ---- design tokens (from always-hyper.jsx) ----
val BgTop = Color(0xFF171F3D)
val BgBottom = Color(0xFF0A0E1A)
val Surface = Color(0xFF12172A)
val LineColor = Color(0xFF232B47)
val Accent1 = Color(0xFF5B7CFF) // electric blue (default theme)
val Accent2 = Color(0xFF9B6BFF) // violet (default theme)
val TextMain = Color(0xFFEDEFF7)
val Muted = Color(0xFF7C87A8)
val MutedDim = Color(0xFF5B6180)
val ThumbDisabled = Color(0xFF4A5170)
val NavBg = Color(0xCC12172A)

fun ThemeColor.toComposeColor(): Color = Color(primaryArgb)
fun ThemeColor.toComposeSecondary(): Color = Color(secondaryArgb)
