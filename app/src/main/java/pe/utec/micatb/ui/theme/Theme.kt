package pe.utec.micatb.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.utec.micatb.R

/**
 * Mundo visual: lamina de micrografia. UI neutra (hueso sobre negro mate de lamina); el color queda para
 * los datos, con los pigmentos de la tincion Gram: safranina (Gram negativo) y violeta cristal (Gram positivo).
 */
@Immutable
class StainColors(
    val gNegative: Color,
    val gNegativeSoft: Color,
    val gPositive: Color,
    val gPositiveSoft: Color,
    val warning: Color,
    val onWarning: Color,
    val ok: Color,
)

private val DarkStain = StainColors(
    gNegative = Color(0xFFE5589A),
    gNegativeSoft = Color(0xFFF2A3C6),
    gPositive = Color(0xFF9F82F0),
    gPositiveSoft = Color(0xFFC6B6F7),
    warning = Color(0xFFE2B33F),
    onWarning = Color(0xFF1B1503),
    ok = Color(0xFF8FC4A2),
)

private val LightStain = StainColors(
    gNegative = Color(0xFFB0206A),
    gNegativeSoft = Color(0xFFD27AA6),
    gPositive = Color(0xFF6445C4),
    gPositiveSoft = Color(0xFF9C86DE),
    warning = Color(0xFF8A6200),
    onWarning = Color(0xFFFFF8E6),
    ok = Color(0xFF2F7A4B),
)

val LocalStain = staticCompositionLocalOf { DarkStain }

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8E3D6), onPrimary = Color(0xFF0E0F10),
    primaryContainer = Color(0xFF2A2D30), onPrimaryContainer = Color(0xFFE8E3D6),
    secondary = Color(0xFFC9C4B7), onSecondary = Color(0xFF0E0F10),
    secondaryContainer = Color(0xFF23262A), onSecondaryContainer = Color(0xFFDDD8CB),
    tertiary = Color(0xFFE5589A), onTertiary = Color(0xFF1A0510),
    tertiaryContainer = Color(0xFF3A1226), onTertiaryContainer = Color(0xFFF7C6DC),
    background = Color(0xFF0E0F10), onBackground = Color(0xFFDDD8CB),
    surface = Color(0xFF0E0F10), onSurface = Color(0xFFDDD8CB),
    surfaceVariant = Color(0xFF1E2124), onSurfaceVariant = Color(0xFFA7A398),
    surfaceContainerLowest = Color(0xFF080909), surfaceContainerLow = Color(0xFF131517),
    surfaceContainer = Color(0xFF17191C), surfaceContainerHigh = Color(0xFF1E2124),
    surfaceContainerHighest = Color(0xFF26292D),
    outline = Color(0xFF6A6E73), outlineVariant = Color(0xFF34383C),
    error = Color(0xFFF2735C), onError = Color(0xFF2B0A04),
    errorContainer = Color(0xFF4A1A12), onErrorContainer = Color(0xFFFFD9D1),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF14161A), onPrimary = Color(0xFFEDE8DB),
    primaryContainer = Color(0xFFD9D3C4), onPrimaryContainer = Color(0xFF14161A),
    secondary = Color(0xFF3E4147), onSecondary = Color(0xFFEDE8DB),
    secondaryContainer = Color(0xFFE0DACB), onSecondaryContainer = Color(0xFF1D1F23),
    tertiary = Color(0xFFB0206A), onTertiary = Color(0xFFFFF1F7),
    tertiaryContainer = Color(0xFFF6D3E4), onTertiaryContainer = Color(0xFF3F0624),
    background = Color(0xFFEDE8DB), onBackground = Color(0xFF14161A),
    surface = Color(0xFFEDE8DB), onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFDDD7C8), onSurfaceVariant = Color(0xFF4A4C50),
    surfaceContainerLowest = Color(0xFFF6F2E8), surfaceContainerLow = Color(0xFFE9E4D6),
    surfaceContainer = Color(0xFFE3DDCE), surfaceContainerHigh = Color(0xFFDDD7C8),
    surfaceContainerHighest = Color(0xFFD5CFBF),
    outline = Color(0xFF76786F), outlineVariant = Color(0xFFBDB7A8),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/** Condensada, para rotulos de instrumento en mayusculas. */
val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_medium, FontWeight.Medium))

private const val TABULAR = "tnum"

private fun t(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0, family: FontFamily = Barlow) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

/** Escala de tipo Material 3 con pasos cortos (Operate): nada por debajo de 12 sp y los datos en 15 sp o mas. */
private val MicaTypography = Typography(
    displaySmall = t(36, 40, FontWeight.SemiBold),
    headlineMedium = t(28, 34, FontWeight.SemiBold),
    headlineSmall = t(24, 30, FontWeight.SemiBold),
    titleLarge = t(22, 28, FontWeight.Medium),
    titleMedium = t(17, 24, FontWeight.Medium, 0.1),
    titleSmall = t(15, 20, FontWeight.Medium, 0.1),
    bodyLarge = t(17, 24),
    bodyMedium = t(15, 21),
    bodySmall = t(13, 18),
    labelLarge = t(16, 20, FontWeight.SemiBold, 0.2),
    labelMedium = t(14, 18, FontWeight.Medium, 0.3),
    labelSmall = t(12, 16, FontWeight.Medium, 0.9, BarlowCondensed),
)

/** Numerales tabulares para conteos, umbrales y tiempos: las cifras no bailan al cambiar. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = TABULAR)

private val MicaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
)

@Composable
fun MicaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalStain provides if (dark) DarkStain else LightStain) {
        MaterialTheme(colorScheme = scheme, typography = MicaTypography, shapes = MicaShapes, content = content)
    }
}

object Mica {
    val stain: StainColors
        @Composable @ReadOnlyComposable get() = LocalStain.current
}
