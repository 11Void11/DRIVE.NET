package com.safecruise.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonYellow,
    secondary = LimeGreen,
    background = DarkBackground,
    surface = DarkCharcoal,
    onPrimary = DarkCharcoal,
    onSecondary = DarkCharcoal,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun SafeCruiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
