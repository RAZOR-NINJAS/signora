package ai.signora.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// MATERIAL 3 EXPRESSIVE SEED & TONAL SYSTEM
// Seeds: Primary #5B5BD6, Secondary #2FA6A1, Tertiary #E8618C
// All color combinations meet WCAG AA contrast (≥ 4.5:1 text, ≥ 3:1 UI elements)
// ============================================================================

val PrimarySeed = Color(0xFF5B5BD6)
val SecondarySeed = Color(0xFF2FA6A1)
val TertiarySeed = Color(0xFFE8618C)

// Pure foundational primitives
val PureBlack = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)

// Material 3 Expressive Extended & Compatibility Tokens
val ExpressivePrimary = Color(0xFF6366F1)
val ExpressivePrimaryLight = Color(0xFFA5B4FC)
val ExpressivePrimaryContainer = Color(0xFF312E81)
val ExpressiveOnPrimaryContainer = Color(0xFFE0E7FF)
val ExpressiveSecondary = Color(0xFF06B6D4)
val ExpressiveSecondaryLight = Color(0xFF67E8F9)
val ExpressiveSecondaryContainer = Color(0xFF164E63)
val ExpressiveTertiary = Color(0xFFF43F5E)
val ExpressiveTertiaryLight = Color(0xFFFDA4AF)
val ExpressiveEmerald = Color(0xFF10B981)
val ExpressiveAmber = Color(0xFFF59E0B)

val SurfaceDark = Color(0xFF090D16)
val SurfaceElevated = Color(0xFF111827)
val SurfaceContainer = Color(0xFF1E293B)
val SurfaceContainerHigh = Color(0xFF334155)
val SurfaceContainerHighest = Color(0xFF475569)

val Slate100 = Color(0xFFF1F5F9)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)

val ExpressiveBorder = Color(0x33818CF8)
val ExpressiveCardBorder = Color(0x22FFFFFF)

val Slate950 = SurfaceDark
val Slate900 = SurfaceElevated
val Slate800 = SurfaceContainer
val Cyan400 = ExpressiveSecondaryLight
val Cyan500 = ExpressiveSecondary
val Magenta400 = ExpressiveTertiaryLight
val Emerald400 = ExpressiveEmerald
val Amber400 = ExpressiveAmber

// Light Color Scheme
val ExpressiveLightColorScheme = lightColorScheme(
    primary = Color(0xFF4C4BB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF0C0664),
    secondary = Color(0xFF006A66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF70F7F0),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = Color(0xFF90425D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3B001B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FD),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBF8FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2F7),
    surfaceContainer = Color(0xFFEFECF2),
    surfaceContainerHigh = Color(0xFFEAE7EC),
    surfaceContainerHighest = Color(0xFFE4E1E6),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF3F0F4),
    inversePrimary = Color(0xFFC2C1FF),
    scrim = PureBlack
)

// Dark Color Scheme
val ExpressiveDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    onPrimary = Color(0xFF1C1387),
    primaryContainer = Color(0xFF3533A0),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFF4EDAD4),
    onSecondary = Color(0xFF003734),
    secondaryContainer = Color(0xFF00504D),
    onSecondaryContainer = Color(0xFF70F7F0),
    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF58122F),
    tertiaryContainer = Color(0xFF732B45),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C0D14),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF11131C),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF2D2E3A),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF08090E),
    surfaceContainerLow = Color(0xFF161824),
    surfaceContainer = Color(0xFF1B1D2A),
    surfaceContainerHigh = Color(0xFF262838),
    surfaceContainerHighest = Color(0xFF323447),
    outline = Color(0xFF8E8EA0),
    outlineVariant = Color(0xFF434455),
    inverseSurface = Color(0xFFE4E1E6),
    inverseOnSurface = Color(0xFF1B1B1F),
    inversePrimary = Color(0xFF4C4BB8),
    scrim = PureBlack
)

// ============================================================================
// SEMANTIC COLORS (Success & Warning containers harmonized with M3 roles)
// ============================================================================

@Immutable
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val warningContainerHigh: Color,
    val onWarningContainer: Color,
    val handRightBone: Color,
    val handLeftBone: Color,
    val handJoint: Color
)

val LightSemanticColors = SemanticColors(
    success = Color(0xFF1A6C35),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA3F5B0),
    onSuccessContainer = Color(0xFF00210A),
    warning = Color(0xFF7B5800),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDF9E),
    warningContainerHigh = Color(0xFFFFCD66),
    onWarningContainer = Color(0xFF261900),
    handRightBone = Color(0xFF00897B),
    handLeftBone = Color(0xFFC2185B),
    handJoint = Color(0xFF004D40)
)

val DarkSemanticColors = SemanticColors(
    success = Color(0xFF88D896),
    onSuccess = Color(0xFF003915),
    successContainer = Color(0xFF005322),
    onSuccessContainer = Color(0xFFA3F5B0),
    warning = Color(0xFFFABA35),
    onWarning = Color(0xFF412D00),
    warningContainer = Color(0xFF5D4200),
    warningContainerHigh = Color(0xFF7C5900),
    onWarningContainer = Color(0xFFFFDF9E),
    handRightBone = Color(0xFF26A69A),
    handLeftBone = Color(0xFFEC407A),
    handJoint = Color(0xFF80CBC4)
)

val LocalSemanticColors = staticCompositionLocalOf { DarkSemanticColors }

// ============================================================================
// SPACING SYSTEM (8dp Grid)
// ============================================================================

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
    val touchTargetMin: Dp = 48.dp,
    val touchTargetPrimary: Dp = 56.dp,
    val actionButtonHeight: Dp = 72.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

// ============================================================================
// EXPRESSIVE SHAPES
// ============================================================================

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ============================================================================
// EXPRESSIVE TYPOGRAPHY
// ============================================================================

val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Monospace styles for technical telemetry (FPS, Latency, Confidence)
val TelemetryMonospace = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.8.sp
)

val TranscriptMonospace = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp
)

// ============================================================================
// APP THEME MODE ENUM
// ============================================================================

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

// ============================================================================
// ROOT THEME COMPOSABLE
// ============================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignoraTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && supportsDynamic -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }

    val semanticColors = if (isDark) DarkSemanticColors else LightSemanticColors
    val spacing = Spacing()

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalSpacing provides spacing
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content
        )
    }
}

// Theme token accessor helpers
object AppTheme {
    val semantic: SemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSemanticColors.current

    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}
