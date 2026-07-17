package com.shyz.alwayshyper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    background = AppBg,
    surface = CardBg,
    primary = AccentBlue,
    secondary = AccentBlue,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AlwaysHyperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
