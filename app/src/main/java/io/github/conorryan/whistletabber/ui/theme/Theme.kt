package io.github.conorryan.whistletabber.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand green, a nod to the feadóg's traditional colours. */
val WhistleGreen = Color(0xFF168A5B)
val WhistleGreenDark = Color(0xFF3FC48C)

private val LightColors = lightColorScheme(
    primary = WhistleGreen,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF11181C),
    surface = Color.White,
    onSurface = Color(0xFF11181C),
    surfaceVariant = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFF687076),
)

private val DarkColors = darkColorScheme(
    primary = WhistleGreenDark,
    onPrimary = Color(0xFF00210F),
    background = Color(0xFF151718),
    onBackground = Color(0xFFECEDEE),
    surface = Color(0xFF151718),
    onSurface = Color(0xFFECEDEE),
    surfaceVariant = Color(0xFF23272A),
    onSurfaceVariant = Color(0xFF9BA1A6),
)

@Composable
fun WhistleTabberTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
