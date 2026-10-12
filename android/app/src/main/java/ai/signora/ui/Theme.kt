package ai.signora.ui

import androidx.compose.runtime.Composable
import ai.signora.ui.theme.*

// Compatibility aliases and delegation to ai.signora.ui.theme
@Composable
fun UnspokenTheme(
    content: @Composable () -> Unit
) {
    SignoraTheme(content = content)
}
