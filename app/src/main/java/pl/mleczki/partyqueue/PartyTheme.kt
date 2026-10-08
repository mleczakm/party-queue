package pl.mleczki.partyqueue

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which colour theme the host chose. [AUTO] follows the phone's setting. */
enum class ThemeMode {
    AUTO, LIGHT, DARK;

    fun next() = entries[(ordinal + 1) % entries.size]
}

/** Party Queue's colours. The three-colour gradient is the brand; orange and lime are sparks. */
object Brand {
    val Pink = Color(0xFFFF2E93)
    val Violet = Color(0xFF7C3AED)
    val Blue = Color(0xFF22B8FF)
    val Orange = Color(0xFFFF9F1C)
    val Lime = Color(0xFFB8F34A)

    val Gradient = listOf(Pink, Violet, Blue)

    fun horizontal(): Brush = Brush.horizontalGradient(Gradient)
    fun diagonal(): Brush = Brush.linearGradient(Gradient)
}

/** True while the dark palette is in use (the system bar style and a few custom drawings need to know). */
val LocalDarkTheme = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4A3FF),
    onPrimary = Color(0xFF1B0B3A),
    primaryContainer = Color(0xFF3B1E7A),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFFF7BBB),
    onSecondary = Color(0xFF3F0020),
    secondaryContainer = Color(0xFF6A1B45),
    onSecondaryContainer = Color(0xFFFFD9EA),
    tertiary = Color(0xFF63D8FF),
    onTertiary = Color(0xFF00364A),
    tertiaryContainer = Color(0xFF004E69),
    onTertiaryContainer = Color(0xFFC3EEFF),
    background = Color(0xFF130A26),
    onBackground = Color(0xFFF3ECFF),
    surface = Color(0xFF1A1030),
    onSurface = Color(0xFFF3ECFF),
    surfaceVariant = Color(0xFF2B1B4B),
    onSurfaceVariant = Color(0xFFCDBFEA),
    surfaceContainerLowest = Color(0xFF100820),
    surfaceContainerLow = Color(0xFF1E1337),
    surfaceContainer = Color(0xFF241741),
    surfaceContainerHigh = Color(0xFF2B1B4B),
    surfaceContainerHighest = Color(0xFF34225A),
    outline = Color(0xFF6A58A0),
    outlineVariant = Color(0xFF3A2A62),
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF3F0010),
    errorContainer = Color(0xFF6B1427),
    onErrorContainer = Color(0xFFFFDADF),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF260A5C),
    secondary = Color(0xFFD81B7E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9EA),
    onSecondaryContainer = Color(0xFF3F0020),
    tertiary = Color(0xFF0A8FC4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8EDFF),
    onTertiaryContainer = Color(0xFF00334A),
    background = Color(0xFFFFF7FC),
    onBackground = Color(0xFF241437),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF241437),
    surfaceVariant = Color(0xFFF1E6FB),
    onSurfaceVariant = Color(0xFF53416E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2FF),
    surfaceContainer = Color(0xFFF6EBFF),
    surfaceContainerHigh = Color(0xFFF0E3FC),
    surfaceContainerHighest = Color(0xFFEADBF8),
    outline = Color(0xFF8B79A8),
    outlineVariant = Color(0xFFDCCBF0),
    error = Color(0xFFBA1A33),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410010),
)

private val PartyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val Base = Typography()
private val PartyTypography = Typography(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold),
    bodyMedium = Base.bodyMedium.copy(fontWeight = FontWeight.Medium),
)

@Composable
fun PartyTheme(mode: ThemeMode = ThemeMode.AUTO, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = PartyTypography,
            shapes = PartyShapes,
        ) {
            // Text outside a Surface would otherwise be black, which vanishes on the dark background.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}
