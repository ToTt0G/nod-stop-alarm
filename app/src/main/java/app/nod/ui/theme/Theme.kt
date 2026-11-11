package app.nod.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nod.R
import app.nod.data.model.ThemeMode

// ---- Palette -------------------------------------------------------------------------------

object NodPalette {
    val Lime = Color(0xFFC8F55A)
    val LimeDeep = Color(0xFF5E8C00)
    val Ink = Color(0xFF0E0F11)
    val Ink2 = Color(0xFF16181B)
    val Ink3 = Color(0xFF1F2226)
    val Ink4 = Color(0xFF2A2E33)
    val Paper = Color(0xFFF5F4EF)
    val Paper2 = Color(0xFFFFFFFF)
    val Paper3 = Color(0xFFECEBE5)
    val Paper4 = Color(0xFFDDDBD3)
    val Coral = Color(0xFFFF6B5A)
    val Sky = Color(0xFF7CC4FF)
}

/** Brand colours that don't map neatly onto Material roles. */
@Immutable
data class NodColors(
    val accent: Color,
    val onAccent: Color,
    val cta: Color,
    val onCta: Color,
    val zone: Color,
    val muted: Color,
    val hairline: Color,
    val card: Color,
    val cardHigh: Color,
    val isDark: Boolean,
)

private val DarkNod = NodColors(
    accent = NodPalette.Lime, onAccent = NodPalette.Ink,
    cta = NodPalette.Lime, onCta = NodPalette.Ink,
    zone = NodPalette.Lime, muted = Color(0xFF9097A0),
    hairline = Color(0x1FFFFFFF), card = NodPalette.Ink2, cardHigh = NodPalette.Ink3, isDark = true,
)

private val LightNod = NodColors(
    accent = NodPalette.Lime, onAccent = NodPalette.Ink,
    cta = NodPalette.Ink, onCta = NodPalette.Lime,
    zone = Color(0xFF6A9A00), muted = Color(0xFF6E7177),
    hairline = Color(0x14000000), card = NodPalette.Paper2, cardHigh = NodPalette.Paper3, isDark = false,
)

private val DarkScheme = darkColorScheme(
    primary = NodPalette.Lime, onPrimary = NodPalette.Ink,
    primaryContainer = Color(0xFF2B3A0C), onPrimaryContainer = NodPalette.Lime,
    secondary = Color(0xFFB9C0C8), onSecondary = NodPalette.Ink,
    background = NodPalette.Ink, onBackground = Color(0xFFF1F1EC),
    surface = NodPalette.Ink, onSurface = Color(0xFFF1F1EC),
    surfaceVariant = NodPalette.Ink3, onSurfaceVariant = Color(0xFF9097A0),
    surfaceContainerLowest = NodPalette.Ink, surfaceContainerLow = NodPalette.Ink2,
    surfaceContainer = NodPalette.Ink2, surfaceContainerHigh = NodPalette.Ink3, surfaceContainerHighest = NodPalette.Ink4,
    outline = NodPalette.Ink4, outlineVariant = Color(0xFF23262A),
    error = NodPalette.Coral, onError = NodPalette.Ink,
    inverseSurface = Color(0xFFF1F1EC), inverseOnSurface = NodPalette.Ink,
)

private val LightScheme = lightColorScheme(
    primary = NodPalette.Ink, onPrimary = NodPalette.Lime,
    primaryContainer = Color(0xFFE6F7C2), onPrimaryContainer = Color(0xFF2A3D00),
    secondary = Color(0xFF4B4F55), onSecondary = Color.White,
    background = NodPalette.Paper, onBackground = NodPalette.Ink,
    surface = NodPalette.Paper, onSurface = NodPalette.Ink,
    surfaceVariant = NodPalette.Paper3, onSurfaceVariant = Color(0xFF6E7177),
    surfaceContainerLowest = Color.White, surfaceContainerLow = NodPalette.Paper2,
    surfaceContainer = NodPalette.Paper2, surfaceContainerHigh = NodPalette.Paper3, surfaceContainerHighest = NodPalette.Paper4,
    outline = NodPalette.Paper4, outlineVariant = Color(0xFFE4E2DB),
    error = Color(0xFFD93A2B), onError = Color.White,
    inverseSurface = NodPalette.Ink, inverseOnSurface = Color(0xFFF1F1EC),
)

// ---- Type ----------------------------------------------------------------------------------

private fun manrope(w: Int) = Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
private fun grotesk(w: Int) = Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))
val Grotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

private val NodTypography = Typography(
    displayLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 76.sp, lineHeight = 76.sp, letterSpacing = (-3).sp),
    displayMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-2).sp),
    displaySmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp),
)

private val NodShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// ---- Motion --------------------------------------------------------------------------------

object NodMotion {
    fun <T> snappy() = spring<T>(dampingRatio = 0.82f, stiffness = 520f)
    fun <T> bouncy() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 380f)
    fun <T> gentle() = spring<T>(dampingRatio = 1f, stiffness = 140f)
    fun <T> emphasized(ms: Int = 420) = tween<T>(ms, easing = FastOutSlowInEasing)
}

// ---- Theme ---------------------------------------------------------------------------------

val LocalNodColors = staticCompositionLocalOf { DarkNod }

object NodTheme {
    val colors: NodColors @Composable get() = LocalNodColors.current
}

@Composable
fun isNodDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun NodTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNodColors provides if (dark) DarkNod else LightNod) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = NodTypography,
            shapes = NodShapes,
            content = content,
        )
    }
}
