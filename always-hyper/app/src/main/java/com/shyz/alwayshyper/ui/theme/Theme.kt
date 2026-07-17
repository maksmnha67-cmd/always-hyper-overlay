package com.shyz.alwayshyper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.shyz.alwayshyper.ThemeColor

/** The currently selected accent colours, available anywhere in the tree. */
data class AppAccent(
    val primary: Color,
    val secondary: Color,
    val onAccent: Color
)

val LocalAppAccent = staticCompositionLocalOf { AppAccent(Accent1, Accent2, Color.White) }

private val AppColorScheme = darkColorScheme(
    background = BgBottom,
    surface = Surface,
    primary = Accent1,
    secondary = Accent2,
    onBackground = TextMain,
    onSurface = TextMain
)

@Composable
fun AlwaysHyperTheme(
    themeColor: ThemeColor = ThemeColor.BLUE,
    content: @Composable () -> Unit
) {
    val primary = themeColor.toComposeColor()
    val secondary = themeColor.toComposeSecondary()
    val onAccent = if (themeColor.isLight) BgBottom else Color.White
    val accent = AppAccent(primary, secondary, onAccent)

    CompositionLocalProvider(LocalAppAccent provides accent) {
        MaterialTheme(
            colorScheme = AppColorScheme.copy(primary = primary, secondary = secondary),
            content = content
        )
    }
}
