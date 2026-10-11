package ai.signora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Cyan400 = Color(0xFF22D3EE)
val Cyan500 = Color(0xFF06B6D4)
val Magenta400 = Color(0xFFE879F9)
val Emerald400 = Color(0xFF34D399)
val Amber400 = Color(0xFFFBBF24)

private val DarkColorScheme = darkColorScheme(
    primary = Cyan400,
    secondary = Magenta400,
    tertiary = Emerald400,
    background = Slate950,
    surface = Slate900,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun SignoraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
