package io.github.conorryan.whistletabber.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Brand green, a nod to the feadóg's traditional colours. */
val WhistleGreen = Color(0xFF11603F)
val WhistleGreenDark = Color(0xFF3FC48C)

/** Brass, the accent: stars, the cursor, the rhythm button. */
val Brass = Color(0xFFC8861A)

private val LightColors = lightColorScheme(
    primary = WhistleGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F0E9),
    onPrimaryContainer = WhistleGreen,
    secondary = Color(0xFF8F5C0E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBF3E4),
    onSecondaryContainer = Color(0xFF7A4E0B),
    tertiary = Color(0xFF8F5C0E),
    onTertiary = Color.White,
    background = Color(0xFFF5F6F2),
    onBackground = Color(0xFF141A16),
    surface = Color(0xFFF5F6F2),
    onSurface = Color(0xFF141A16),
    surfaceVariant = Color(0xFFEEF1EC),
    onSurfaceVariant = Color(0xFF5B655E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF8F9F6),
    surfaceContainerHighest = Color(0xFFEEF1EC),
    inverseSurface = Color(0xFF141A16),
    inverseOnSurface = Color.White,
    inversePrimary = WhistleGreenDark,
    outline = Color(0xFFDCE1DA),
    outlineVariant = Color(0xFFE6E9E3),
    error = Color(0xFFA33A1E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = WhistleGreenDark,
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF1D3A2C),
    onPrimaryContainer = Color(0xFFB5EDCF),
    secondary = Color(0xFFE0B064),
    onSecondary = Color(0xFF2E1F05),
    secondaryContainer = Color(0xFF3A2C14),
    onSecondaryContainer = Color(0xFFF2D3A0),
    tertiary = Color(0xFFE0B064),
    onTertiary = Color(0xFF2E1F05),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE8ECE8),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE8ECE8),
    surfaceVariant = Color(0xFF242B27),
    onSurfaceVariant = Color(0xFFA3ADA6),
    surfaceContainerLowest = Color(0xFF0B0E0C),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF181D1A),
    surfaceContainerHigh = Color(0xFF1E2420),
    surfaceContainerHighest = Color(0xFF242B27),
    inverseSurface = Color(0xFF242B27),
    inverseOnSurface = Color(0xFFE8ECE8),
    inversePrimary = WhistleGreenDark,
    outline = Color(0xFF3A433D),
    outlineVariant = Color(0xFF2A322D),
    error = Color(0xFFFF8A70),
    onError = Color(0xFF3A0B00),
)

/** Colours outside Material's scheme. */
@Immutable
data class WhistleColors(
    /** A filled star. */
    val star: Color,
    /** The player bar's play button. */
    val play: Color,
    val onPlay: Color,
)

private val LightExtras = WhistleColors(star = Brass, play = WhistleGreenDark, onPlay = Color(0xFF0A2417))
private val DarkExtras = WhistleColors(star = Color(0xFFE0B064), play = WhistleGreenDark, onPlay = Color(0xFF0A2417))

val LocalWhistleColors = staticCompositionLocalOf { LightExtras }

private val Base = Typography()

/** Heavy, tight headings over a plain body: the big screen titles carry the look. */
private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** The small caps-like label over a group of settings or under the app name. */
val SectionLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WhistleTabberTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWhistleColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
