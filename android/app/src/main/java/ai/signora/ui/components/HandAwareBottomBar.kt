package ai.signora.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.signora.models.DebounceState
import ai.signora.models.DwellStatus
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.TelemetryMonospace

/**
 * Hand-aware responsive bottom deck for Unspoken.
 *
 * Implements Phase 6 & Phase 7:
 * - Expanded State (no hand): full-featured command deck with large ActiveSignDisplay,
 *   transcript box, action buttons, and navigation bar.
 * - Collapsed State (hand detected): morphs into a floating toolbar pill on the left
 *   holding mini sign badge, mini transcript chip, and Space/Delete/Clear buttons,
 *   plus a separate large rounded-square Speak button on the right.
 * - Single interruptible animated progress value (0 = expanded, 1 = collapsed).
 * - Reads progress in layout/graphicsLayer/draw lambdas to eliminate per-frame recomposition.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HandAwareBottomBar(
    shouldCollapse: Boolean,
    debounceState: DebounceState,
    onPinExpanded: () -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSpeak: () -> Unit,
    synthesizedSentence: String = "",
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Trigger subtle haptic tick on state change
    LaunchedEffect(shouldCollapse) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Single interruptible animated progress value using Expressive motionScheme
    val collapseProgress by animateFloatAsState(
        targetValue = if (shouldCollapse) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "BottomBarCollapse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        if (collapseProgress < 0.99f) {
            // ====================================================================
            // 6.1 EXPANDED STATE (Fades out / scales down as collapseProgress -> 1)
            // ====================================================================
            Surface(
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp
                ),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - collapseProgress
                        scaleY = 1f - (collapseProgress * 0.15f)
                        translationY = collapseProgress * 50f
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
                ) {
                    // Drag handle affordance
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )

                    // Active Sign Display with circular dwell ring and confidence
                    ActiveSignDisplay(
                        candidate = debounceState.candidate,
                        confidence = debounceState.confidence,
                        dwellProgressProvider = { debounceState.dwellProgress },
                        status = debounceState.status
                    )

                    // Transcript Text Box
                    TranscriptField(
                        transcript = debounceState.transcript,
                        synthesizedSentence = synthesizedSentence,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Action Button Row: Space, Delete, Clear, Speak
                    ActionButtonRow(
                        transcript = debounceState.transcript,
                        onSpace = onSpace,
                        onDelete = onDelete,
                        onClear = onClear,
                        onSpeak = onSpeak,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (collapseProgress > 0.01f) {
            // ====================================================================
            // 6.2 COLLAPSED STATE (Floating Toolbar Pill + Large Square Speak Button)
            // ====================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small)
                    .graphicsLayer {
                        alpha = collapseProgress
                        scaleX = 0.85f + (collapseProgress * 0.15f)
                        scaleY = 0.85f + (collapseProgress * 0.15f)
                        translationY = (1f - collapseProgress) * 40f
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // (a) Floating Toolbar Pill (holds mini badge, mini transcript chip, Space, Del, Clear)
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            // Tapping collapsed pill pins expanded for 5 seconds
                            onPinExpanded()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Mini Active Letter Badge with circular dwell ring
                        Box(
                            modifier = Modifier.size(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                            val successColor = AppTheme.semantic.success
                            val isCommitted = debounceState.status == DwellStatus.COMMITTED

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 3.5.dp.toPx()
                                val arcRadius = (size.minDimension - strokeWidth) / 2f
                                val arcCenter = center

                                // Background track
                                drawCircle(
                                    color = trackColor,
                                    radius = arcRadius,
                                    center = arcCenter,
                                    style = Stroke(width = strokeWidth)
                                )

                                // Active dwell arc
                                val progress = debounceState.dwellProgress
                                if (progress > 0f) {
                                    drawArc(
                                        color = if (isCommitted) successColor else primaryColor,
                                        startAngle = -90f,
                                        sweepAngle = progress * 360f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                            }

                            // Center sign glyph or letter
                            if (debounceState.candidate == "SPACE") {
                                Icon(
                                    imageVector = Icons.Filled.SpaceBar,
                                    contentDescription = "Space",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = if (debounceState.candidate.isNotBlank()) debounceState.candidate else "—",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Mini Transcript Chip
                        val displayMini = ai.signora.engine.DebounceEngine.formatTranscriptForDisplay(debounceState.transcript)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = if (displayMini.isNotBlank()) displayMini else "Listening...",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        // Compact Action Icons: Space, Del, Clear
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onSpace,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SpaceBar,
                                    contentDescription = "Space",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onDelete,
                                enabled = debounceState.transcript.isNotEmpty(),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete",
                                    tint = if (debounceState.transcript.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onClear,
                                enabled = debounceState.transcript.isNotEmpty(),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear",
                                    tint = if (debounceState.transcript.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(AppTheme.spacing.small))

                // (b) Separate Large Rounded-Square Speak Button (~72dp, primaryContainer)
                val speakInteractionSource = remember { MutableInteractionSource() }
                val isSpeakPressed by speakInteractionSource.collectIsPressedAsState()
                val speakScale by animateFloatAsState(
                    targetValue = if (isSpeakPressed) 0.94f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "SpeakButtonScale"
                )

                Surface(
                    onClick = onSpeak,
                    enabled = debounceState.transcript.isNotEmpty(),
                    shape = RoundedCornerShape(22.dp),
                    color = if (debounceState.transcript.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    contentColor = if (debounceState.transcript.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    interactionSource = speakInteractionSource,
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            scaleX = speakScale
                            scaleY = speakScale
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak Transcript",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
