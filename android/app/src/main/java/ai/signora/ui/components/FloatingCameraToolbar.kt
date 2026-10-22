package ai.signora.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.signora.ui.theme.SignoraTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingCameraToolbar(
    isFrontCamera: Boolean,
    isTorchOn: Boolean,
    isPaused: Boolean,
    onToggleCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSpeechToSign: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
        ),
        modifier = modifier
    ) {
        // 1. Hearing Person Speaks (Speech to Sign)
        if (onOpenSpeechToSign != null) {
            IconButton(
                onClick = onOpenSpeechToSign,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = "Hearing Person Speaks to ISL",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Flip Camera
        IconButton(
            onClick = onToggleCamera,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FlipCameraAndroid,
                contentDescription = if (isFrontCamera) "Switch to Back Camera" else "Switch to Front Camera",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        // 3. Torch
        IconButton(
            onClick = onToggleTorch,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Toggle Torch",
                tint = if (isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // 4. Pause / Resume
        IconButton(
            onClick = onTogglePause,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume Tracking" else "Pause Tracking",
                tint = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        // 5. Settings
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(name = "FloatingCameraToolbar Preview")
@Composable
private fun FloatingCameraToolbarPreview() {
    SignoraTheme(dynamicColor = false) {
        FloatingCameraToolbar(
            isFrontCamera = true,
            isTorchOn = false,
            isPaused = false,
            onToggleCamera = {},
            onToggleTorch = {},
            onTogglePause = {},
            onOpenSettings = {}
        )
    }
}
