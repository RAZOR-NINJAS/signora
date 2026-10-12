package ai.signora.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.SignoraTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTopBar(
    title: String = "Unspoken",
    fps: Float = 30f,
    isDebugOpen: Boolean = false,
    isDark: Boolean = true,
    onToggleDebug: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small)
                .heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
        ) {
            // Emphasized Title with long-press debug activation
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onToggleDebug
                    )
                    .padding(horizontal = AppTheme.spacing.small, vertical = AppTheme.spacing.extraSmall)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Real-time FPS Telemetry Pill
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (fps >= 24f) AppTheme.semantic.success else AppTheme.semantic.warning
                            )
                    )
                    Text(
                        text = "${fps.toInt()} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            // Debug Indicator icon when debug mode is engaged
            if (isDebugOpen) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Debug Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick Theme Switcher
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview(name = "AppTopBar Light")
@Composable
private fun AppTopBarPreviewLight() {
    SignoraTheme(dynamicColor = false) {
        AppTopBar(fps = 30f, isDark = false)
    }
}

@Preview(name = "AppTopBar Dark")
@Composable
private fun AppTopBarPreviewDark() {
    SignoraTheme(dynamicColor = false) {
        AppTopBar(fps = 29f, isDark = true, isDebugOpen = true)
    }
}
