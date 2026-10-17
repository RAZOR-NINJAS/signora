package ai.signora.ui.theme

import android.app.WallpaperManager
import android.content.Context
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
import androidx.compose.ui.graphics.toArgb
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
// WALLPAPER-ADAPTIVE DYNAMIC COLOR SCHEME SYNTHESIZER
// Automatically extracts dominant tones from user's wallpaper (API 27+)
// and dynamically creates a harmonized Material 3 palette matching their wallpaper!
// ============================================================================

/**
 * Extracts the primary color from the current system wallpaper using WallpaperManager.
 */
fun extractWallpaperPrimaryColor(context: Context): Color? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        try {
            val wm = WallpaperManager.getInstance(context)
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val primary = colors?.primaryColor
            if (primary != null) {
                return Color(primary.toArgb())
            }
        } catch (_: Throwable) {
            // Gracefully handled if restricted by OEM or device policy
        }
    }
    return null
}

/**
 * Builds a complete Material 3 ColorScheme dynamically derived from any wallpaper seed color.
 * Works for any wallpaper color (pink, cyan, purple, amber, emerald, midnight, etc.).
 */
fun buildSchemeFromWallpaper(seed: Color, isDark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val hue = hsv[0]
    val sat = hsv[1].coerceIn(0.20f, 0.85f)

    fun colorFromHsv(h: Float, s: Float, v: Float): Color {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        return Color(rgb)
    }

    return if (isDark) {
        darkColorScheme(
            primary = colorFromHsv(hue, sat * 0.70f, 0.88f),
            onPrimary = colorFromHsv(hue, sat, 0.15f),
            primaryContainer = colorFromHsv(hue, sat * 0.90f, 0.30f),
            onPrimaryContainer = colorFromHsv(hue, sat * 0.25f, 0.95f),
            secondary = colorFromHsv((hue + 25f) % 360f, sat * 0.65f, 0.82f),
            onSecondary = colorFromHsv(hue, sat, 0.18f),
            secondaryContainer = colorFromHsv((hue + 25f) % 360f, sat * 0.80f, 0.28f),
            onSecondaryContainer = colorFromHsv((hue + 25f) % 360f, sat * 0.20f, 0.92f),
            background = colorFromHsv(hue, sat * 0.15f, 0.08f),
            onBackground = Color(0xFFF1F5F9),
            surface = colorFromHsv(hue, sat * 0.18f, 0.10f),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = colorFromHsv(hue, sat * 0.25f, 0.16f),
            onSurfaceVariant = Color(0xFFCBD5E1),
            surfaceContainerLowest = colorFromHsv(hue, sat * 0.12f, 0.05f),
            surfaceContainerLow = colorFromHsv(hue, sat * 0.15f, 0.08f),
            surfaceContainer = colorFromHsv(hue, sat * 0.18f, 0.11f),
            surfaceContainerHigh = colorFromHsv(hue, sat * 0.22f, 0.15f),
            surfaceContainerHighest = colorFromHsv(hue, sat * 0.26f, 0.20f),
            outline = colorFromHsv(hue, sat * 0.40f, 0.50f),
            outlineVariant = colorFromHsv(hue, sat * 0.25f, 0.25f),
            inverseSurface = Color(0xFFF1F5F9),
            inverseOnSurface = colorFromHsv(hue, sat * 0.18f, 0.10f),
            inversePrimary = colorFromHsv(hue, sat, 0.50f),
            scrim = PureBlack
        )
    } else {
        lightColorScheme(
            primary = colorFromHsv(hue, sat, 0.45f),
            onPrimary = PureWhite,
            primaryContainer = colorFromHsv(hue, sat * 0.30f, 0.92f),
            onPrimaryContainer = colorFromHsv(hue, sat, 0.18f),
            secondary = colorFromHsv((hue + 25f) % 360f, sat * 0.80f, 0.45f),
            onSecondary = PureWhite,
            secondaryContainer = colorFromHsv((hue + 25f) % 360f, sat * 0.25f, 0.93f),
            onSecondaryContainer = colorFromHsv((hue + 25f) % 360f, sat, 0.20f),
            background = colorFromHsv(hue, sat * 0.08f, 0.98f),
            onBackground = Color(0xFF0F172A),
            surface = colorFromHsv(hue, sat * 0.10f, 0.98f),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = colorFromHsv(hue, sat * 0.18f, 0.92f),
            onSurfaceVariant = Color(0xFF475569),
            surfaceContainerLowest = PureWhite,
            surfaceContainerLow = colorFromHsv(hue, sat * 0.06f, 0.96f),
            surfaceContainer = colorFromHsv(hue, sat * 0.10f, 0.94f),
            surfaceContainerHigh = colorFromHsv(hue, sat * 0.14f, 0.90f),
            surfaceContainerHighest = colorFromHsv(hue, sat * 0.18f, 0.86f),
            outline = colorFromHsv(hue, sat * 0.40f, 0.50f),
            outlineVariant = colorFromHsv(hue, sat * 0.20f, 0.80f),
            inverseSurface = Color(0xFF0F172A),
            inverseOnSurface = PureWhite,
            inversePrimary = colorFromHsv(hue, sat * 0.70f, 0.88f),
            scrim = PureBlack
        )
    }
}

val NeonSynthwaveDarkColorScheme = darkColorScheme(
    primary = Color(0xFF06B6D4),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0E4E5B),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = Color(0xFFF43F5E),
    onSecondary = Color(0xFF4C0519),
    secondaryContainer = Color(0xFF881337),
    onSecondaryContainer = Color(0xFFFFE4E6),
    tertiary = Color(0xFFA855F7),
    onTertiary = Color(0xFF3B0764),
    tertiaryContainer = Color(0xFF581C87),
    onTertiaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFF0A0A14),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F0F1E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E1E38),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF05050C),
    surfaceContainerLow = Color(0xFF121224),
    surfaceContainer = Color(0xFF18182E),
    surfaceContainerHigh = Color(0xFF21213D),
    surfaceContainerHighest = Color(0xFF2C2C4E),
    outline = Color(0xFF06B6D4),
    outlineVariant = Color(0xFF2A2A4E),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF0A0A14),
    inversePrimary = Color(0xFF0891B2),
    scrim = PureBlack
)

val EmeraldMatrixDarkColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF047857),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF115E59),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF050F0A),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF091710),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF132A1E),
    onSurfaceVariant = Color(0xFFA7F3D0),
    surfaceContainerLowest = Color(0xFF020805),
    surfaceContainerLow = Color(0xFF0D1E16),
    surfaceContainer = Color(0xFF13281E),
    surfaceContainerHigh = Color(0xFF1A3427),
    surfaceContainerHighest = Color(0xFF224233),
    outline = Color(0xFF10B981),
    outlineVariant = Color(0xFF1B3B2B),
    inverseSurface = Color(0xFFECFDF5),
    inverseOnSurface = Color(0xFF050F0A),
    inversePrimary = Color(0xFF059669),
    scrim = PureBlack
)

val SolarAmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFBBF24),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = Color(0xFFF97316),
    onSecondary = Color(0xFF4A1800),
    secondaryContainer = Color(0xFF9A3412),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF110C05),
    onBackground = Color(0xFFFFFBEB),
    surface = Color(0xFF171107),
    onSurface = Color(0xFFFFFBEB),
    surfaceVariant = Color(0xFF291E0C),
    onSurfaceVariant = Color(0xFFFDE68A),
    surfaceContainerLowest = Color(0xFF0A0702),
    surfaceContainerLow = Color(0xFF1E160A),
    surfaceContainer = Color(0xFF261C0D),
    surfaceContainerHigh = Color(0xFF322511),
    surfaceContainerHighest = Color(0xFF402F17),
    outline = Color(0xFFF59E0B),
    outlineVariant = Color(0xFF3C2C12),
    inverseSurface = Color(0xFFFFFBEB),
    inverseOnSurface = Color(0xFF110C05),
    inversePrimary = Color(0xFFD97706),
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
) {
    val handRightJoint: Color get() = handJoint
    val handLeftJoint: Color get() = handJoint
}

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
// APP THEME MODE & PALETTE ENUMS
// ============================================================================

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class AppPalette(val displayName: String, val subtitle: String, val previewColor: Color) {
    WALLPAPER("Wallpaper Adaptive", "Auto-detects colors from system wallpaper (Material You)", Color(0xFF6750A4)),
    EXPRESSIVE_INDIGO("Expressive Indigo", "Classic Signora brand deep indigo", Color(0xFF6366F1)),
    NEON_CYBER("Neon Synthwave", "Electric cyan & hot magenta", Color(0xFF06B6D4)),
    EMERALD_MATRIX("Matrix Emerald", "Cyber emerald & fresh mint", Color(0xFF10B981)),
    SOLAR_AMBER("Solar Gold", "Radiant gold & warm amber", Color(0xFFF59E0B))
}

// ============================================================================
// ROOT THEME COMPOSABLE
// ============================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignoraTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    selectedPalette: AppPalette = AppPalette.WALLPAPER,
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

    val colorScheme = when (selectedPalette) {
        AppPalette.WALLPAPER -> {
            val detectedColor = extractWallpaperPrimaryColor(context)
            when {
                dynamicColor && supportsDynamic -> {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                detectedColor != null -> {
                    buildSchemeFromWallpaper(detectedColor, isDark)
                }
                isDark -> ExpressiveDarkColorScheme
                else -> ExpressiveLightColorScheme
            }
        }
        AppPalette.EXPRESSIVE_INDIGO -> if (isDark) ExpressiveDarkColorScheme else ExpressiveLightColorScheme
        AppPalette.NEON_CYBER -> if (isDark) NeonSynthwaveDarkColorScheme else ExpressiveLightColorScheme
        AppPalette.EMERALD_MATRIX -> if (isDark) EmeraldMatrixDarkColorScheme else ExpressiveLightColorScheme
        AppPalette.SOLAR_AMBER -> if (isDark) SolarAmberDarkColorScheme else ExpressiveLightColorScheme
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
