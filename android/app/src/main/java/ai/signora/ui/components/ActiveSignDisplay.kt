package ai.signora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.signora.models.DwellStatus
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.SignoraTheme

@Composable
fun ActiveSignDisplay(
    candidate: String,
    confidence: Float,
    dwellProgressProvider: () -> Float,
    status: DwellStatus,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Trigger haptic tick on commit
    LaunchedEffect(status) {
        if (status == DwellStatus.COMMITTED) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Letter scale spring on candidate change
    val letterScale by animateFloatAsState(
        targetValue = if (candidate.isNotBlank()) 1.0f else 0.8f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "LetterSpring"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
    val successColor = AppTheme.semantic.success
    val isCommitted = status == DwellStatus.COMMITTED

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        modifier = modifier.size(140.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Thick (9dp) Circular Dwell Ring evaluated ONLY in Draw Phase (Zero Recompositions)
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val strokeWidth = 9.dp.toPx()
                val progress = dwellProgressProvider().coerceIn(0f, 1f)

                // Background track
                drawCircle(
                    color = trackColor,
                    style = Stroke(width = strokeWidth)
                )

                // Active Dwell Arc
                if (progress > 0f) {
                    drawArc(
                        color = if (isCommitted) successColor else primaryColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Central State / Candidate
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = letterScale
                    scaleY = letterScale
                }
            ) {
                if (candidate.isNotBlank()) {
                    if (candidate == "SPACE") {
                        Icon(
                            imageVector = Icons.Filled.SpaceBar,
                            contentDescription = "Space",
                            tint = if (isCommitted) successColor else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "SPACE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = candidate,
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                            color = if (isCommitted) successColor else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }

                    if (confidence > 0f) {
                        Text(
                            text = "${(confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val statusText = when (status) {
                        DwellStatus.NO_HAND -> "NO HAND"
                        DwellStatus.LOW_LIGHT -> "LOW LIGHT"
                        DwellStatus.ERROR -> "ERROR"
                        else -> "READY"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(name = "ActiveSignDisplay Holding")
@Composable
private fun ActiveSignDisplayHoldingPreview() {
    SignoraTheme(dynamicColor = false) {
        ActiveSignDisplay(
            candidate = "B",
            confidence = 0.94f,
            dwellProgressProvider = { 0.65f },
            status = DwellStatus.HOLDING
        )
    }
}

@Preview(name = "ActiveSignDisplay Committed")
@Composable
private fun ActiveSignDisplayCommittedPreview() {
    SignoraTheme(dynamicColor = false) {
        ActiveSignDisplay(
            candidate = "A",
            confidence = 0.98f,
            dwellProgressProvider = { 1.0f },
            status = DwellStatus.COMMITTED
        )
    }
}
