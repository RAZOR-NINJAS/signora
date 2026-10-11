package ai.signora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 Expressive Color System
val ExpressivePrimary = Color(0xFF6366F1)         // Electric Indigo
val ExpressivePrimaryLight = Color(0xFFA5B4FC)    // Soft Indigo
val ExpressivePrimaryContainer = Color(0xFF312E81)// Deep Indigo Container
val ExpressiveOnPrimaryContainer = Color(0xFFE0E7FF)

val ExpressiveSecondary = Color(0xFF06B6D4)       // Vibrant Cyan / Teal
val ExpressiveSecondaryLight = Color(0xFF67E8F9)
val ExpressiveSecondaryContainer = Color(0xFF164E63)

val ExpressiveTertiary = Color(0xFFF43F5E)        // Expressive Rose / Coral
val ExpressiveTertiaryLight = Color(0xFFFDA4AF)

val ExpressiveEmerald = Color(0xFF10B981)         // Material Emerald
val ExpressiveAmber = Color(0xFFF59E0B)           // Material Amber

val PureBlack = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF090D16)               // Deep Material Obsidian
val SurfaceElevated = Color(0xFF111827)           // Material Slate 900
val SurfaceContainer = Color(0xFF1E293B)          // Material Slate 800
val SurfaceContainerHigh = Color(0xFF334155)      // Material Slate 700
val SurfaceContainerHighest = Color(0xFF475569)

val Slate100 = Color(0xFFF1F5F9)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)

val ExpressiveBorder = Color(0x33818CF8)          // Subtle Indigo-tinted border
val ExpressiveCardBorder = Color(0x22FFFFFF)

// Compatibility aliases
val Slate950 = SurfaceDark
val Slate900 = SurfaceElevated
val Slate800 = SurfaceContainer
val Cyan400 = ExpressiveSecondaryLight
val Cyan500 = ExpressiveSecondary
val Magenta400 = ExpressiveTertiaryLight
val Emerald400 = ExpressiveEmerald
val Amber400 = ExpressiveAmber

private val ExpressiveColorScheme = darkColorScheme(
    primary = ExpressivePrimaryLight,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = ExpressivePrimaryContainer,
    onPrimaryContainer = ExpressiveOnPrimaryContainer,
    secondary = ExpressiveSecondaryLight,
    onSecondary = Color(0xFF083344),
    secondaryContainer = ExpressiveSecondaryContainer,
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = ExpressiveTertiaryLight,
    onTertiary = Color(0xFF4C0519),
    background = SurfaceDark,
    onBackground = Slate100,
    surface = SurfaceElevated,
    onSurface = Slate100,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = Slate300,
    outline = ExpressiveBorder
)

@Composable
fun UnspokenTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ExpressiveColorScheme,
        content = content
    )
}

// Compatibility wrapper
@Composable
fun SignoraTheme(
    content: @Composable () -> Unit
) {
    UnspokenTheme(content = content)
}
