package ai.signora.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.SignoraTheme

@Composable
fun ActionButtonRow(
    transcript: String,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTranscriptEmpty = transcript.isBlank()
    val fontScale = LocalDensity.current.fontScale
    val isVeryLargeFont = fontScale > 1.3f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.spacing.actionButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. SPACE
        ExpressiveActionButton(
            label = "SPACE",
            icon = Icons.Default.SpaceBar,
            contentDescription = "Insert Space",
            enabled = true,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            isLargeFont = isVeryLargeFont,
            onClick = onSpace,
            modifier = Modifier.weight(1f)
        )

        // 2. DELETE (Backspace)
        ExpressiveActionButton(
            label = "DEL",
            icon = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Delete Last Letter",
            enabled = !isTranscriptEmpty,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            isLargeFont = isVeryLargeFont,
            onClick = onDelete,
            modifier = Modifier.weight(1f)
        )

        // 3. CLEAR (ErrorContainer)
        ExpressiveActionButton(
            label = "CLEAR",
            icon = Icons.Default.DeleteOutline,
            contentDescription = "Clear Entire Transcript",
            enabled = !isTranscriptEmpty,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            isLargeFont = isVeryLargeFont,
            onClick = onClear,
            modifier = Modifier.weight(1f)
        )

        // 4. SPEAK (Primary)
        ExpressiveActionButton(
            label = "SPEAK",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Vocalize Transcript",
            enabled = !isTranscriptEmpty,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            isLargeFont = isVeryLargeFont,
            onClick = onSpeak,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExpressiveActionButton(
    label: String,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    isLargeFont: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth physics spring scale on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "ButtonScale"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = AppTheme.spacing.small, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp)
            )

            if (!isLargeFont) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "ActionButtonRow Light")
@Composable
private fun ActionButtonRowPreviewLight() {
    SignoraTheme(dynamicColor = false) {
        ActionButtonRow(
            transcript = "HELLO WORLD",
            onSpace = {},
            onDelete = {},
            onClear = {},
            onSpeak = {}
        )
    }
}

@Preview(name = "ActionButtonRow Dark Empty")
@Composable
private fun ActionButtonRowPreviewDarkEmpty() {
    SignoraTheme(dynamicColor = false) {
        ActionButtonRow(
            transcript = "",
            onSpace = {},
            onDelete = {},
            onClear = {},
            onSpeak = {}
        )
    }
}
