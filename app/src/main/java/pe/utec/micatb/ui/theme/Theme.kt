package pe.utec.micatb.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import pe.utec.micatb.R

/**
 * Sistema "Clinical Diagnostic Precision" (Stitch): lienzo claro e higienico, teal clinico como accion,
 * rojo reservado a los bacilos detectados. Los estados llevan lavado claro y texto oscuro propios.
 */
@Immutable
class StainColors(
    val gNegative: Color,
    val gNegativeSoft: Color,
    val gPositive: Color,
    val gPositiveSoft: Color,
    val warning: Color,
    val ok: Color,
    val dangerWash: Color,
    val onDangerWash: Color,
    val warningWash: Color,
    val okWash: Color,
    val cyanWash: Color,
    val cyanBorder: Color,
)

private val Stain = StainColors(
    gNegative = Color(0xFFEF4444),
    gNegativeSoft = Color(0xFFFCA5A5),
    gPositive = Color(0xFF49607C),
    gPositiveSoft = Color(0xFFB0C9E8),
    warning = Color(0xFF92400E),
    ok = Color(0xFF047857),
    dangerWash = Color(0xFFFEE2E2),
    onDangerWash = Color(0xFF991B1B),
    warningWash = Color(0xFFFEF3C7),
    okWash = Color(0xFFD1FAE5),
    cyanWash = Color(0xFFE8F7F7),
    cyanBorder = Color(0xFFCCECEC),
)

val LocalStain = staticCompositionLocalOf { Stain }

private val Colors = lightColorScheme(
    primary = Color(0xFF006768), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF008284), onPrimaryContainer = Color(0xFFF3FFFE),
    secondary = Color(0xFF49607C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC7DFFF), onSecondaryContainer = Color(0xFF4B637E),
    tertiary = Color(0xFF00685F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF008378), onTertiaryContainer = Color(0xFFF4FFFC),
    background = Color(0xFFF7F9FF), onBackground = Color(0xFF001D32),
    surface = Color(0xFFF7F9FF), onSurface = Color(0xFF001D32),
    surfaceVariant = Color(0xFFCEE5FF), onSurfaceVariant = Color(0xFF3D4949),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEDF4FF),
    surfaceContainer = Color(0xFFE3EFFF), surfaceContainerHigh = Color(0xFFD8EAFF),
    surfaceContainerHighest = Color(0xFFCEE5FF),
    outline = Color(0xFF6D7979), outlineVariant = Color(0xFFBCC9C9),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
)

// Las fuentes son variables: un archivo por familia y el peso sale de la variacion "wght".
private fun family(res: Int, vararg weights: FontWeight) =
    FontFamily(weights.map { w -> Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) })

private val SpaceGrotesk = family(R.font.space_grotesk, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
private val Manrope = family(R.font.manrope, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
val JetBrainsMono = family(R.font.jetbrains_mono, FontWeight.Medium, FontWeight.SemiBold)

private const val TABULAR = "tnum"

private fun t(size: Int, line: Int, weight: FontWeight, font: FontFamily, tracking: Double = 0.0) =
    TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

/**
 * Escala de Stitch: Space Grotesk para titulares y estados, Manrope para contenido y botones,
 * JetBrains Mono para identificadores, rotulos y telemetria.
 */
private val MicaTypography = Typography(
    displaySmall = t(36, 40, FontWeight.SemiBold, SpaceGrotesk, -0.02),
    headlineLarge = t(30, 38, FontWeight.SemiBold, SpaceGrotesk, -0.02),
    headlineMedium = t(24, 30, FontWeight.SemiBold, SpaceGrotesk, -0.015),
    headlineSmall = t(20, 26, FontWeight.SemiBold, SpaceGrotesk, -0.01),
    titleLarge = t(18, 24, FontWeight.SemiBold, Manrope, -0.01),
    titleMedium = t(16, 22, FontWeight.SemiBold, Manrope),
    titleSmall = t(14, 20, FontWeight.SemiBold, Manrope),
    bodyLarge = t(16, 24, FontWeight.Normal, Manrope),
    bodyMedium = t(14, 20, FontWeight.Normal, Manrope),
    bodySmall = t(12, 16, FontWeight.Normal, Manrope),
    labelLarge = t(16, 20, FontWeight.SemiBold, Manrope),
    labelMedium = t(12, 16, FontWeight.Medium, JetBrainsMono, 0.4),
    labelSmall = t(11, 14, FontWeight.SemiBold, JetBrainsMono, 0.5),
)

/** Numerales tabulares para conteos, umbrales y tiempos: las cifras no bailan al cambiar. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = TABULAR)

/** Radios de Stitch: 4 en etiquetas, 8 en contenedores y botones, 12 en tarjetas grandes. */
private val MicaShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun MicaTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    CompositionLocalProvider(LocalStain provides Stain) {
        MaterialTheme(colorScheme = Colors, typography = MicaTypography, shapes = MicaShapes, content = content)
    }
}

object Mica {
    val stain: StainColors
        @Composable @ReadOnlyComposable get() = LocalStain.current
}
