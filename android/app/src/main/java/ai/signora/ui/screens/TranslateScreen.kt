package ai.signora.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.signora.engine.HandPresenceDetector
import ai.signora.models.*
import ai.signora.profiler.TrackingProfiler
import ai.signora.recovery.AutoAssetManager
import ai.signora.ui.components.*
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.PureBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,       // Thumb
    0 to 5, 5 to 6, 6 to 7, 7 to 8,       // Index
    0 to 9, 9 to 10, 10 to 11, 11 to 12,  // Middle
    0 to 13, 13 to 14, 14 to 15, 15 to 16,// Ring
    0 to 17, 17 to 18, 18 to 19, 19 to 20 // Pinky
)

@Composable
fun TranslateScreen(
    previewView: PreviewView,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    debounceState: DebounceState,
    handLandmarks: List<HandLandmarks>,
    currentMode: RecognitionMode,
    isMuted: Boolean,
    isFrontCamera: Boolean,
    isTorchOn: Boolean,
    isPaused: Boolean,
    fps: Float,
    isDark: Boolean,
    autoCollapseEnabled: Boolean = true,
    latestFrameId: Long = 0L,
    assetStatus: AutoAssetManager.AssetStatus,
    onModeChange: (RecognitionMode) -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSpace: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDebugOpen by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var isPinnedExpanded by remember { mutableStateOf(false) }
    var shouldCollapse by remember { mutableStateOf(false) }

    LaunchedEffect(handLandmarks.isNotEmpty(), autoCollapseEnabled, isPinnedExpanded) {
        if (!autoCollapseEnabled || isPinnedExpanded) {
            shouldCollapse = false
            return@LaunchedEffect
        }
        if (handLandmarks.isNotEmpty()) {
            delay(150L) // 150ms enter hysteresis
            shouldCollapse = true
        } else {
            delay(800L) // 800ms dropout tolerance
            shouldCollapse = false
        }
    }

    // Auto-hiding ISL 5-of-26 notification chip (hides after 4s)
    var islBannerVisible by remember(currentMode) { mutableStateOf(currentMode == RecognitionMode.ISL) }
    LaunchedEffect(currentMode) {
        if (currentMode == RecognitionMode.ISL) {
            islBannerVisible = true
            delay(4000L)
            islBannerVisible = false
        } else {
            islBannerVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // 1. Fullscreen Camera Viewport
        AndroidView(
            factory = { _ ->
                (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Exact Landmark Overlay Alignment (Phase 3: FILL_CENTER scale/crop + front-camera horizontal mirror)
        val rightBone = AppTheme.semantic.handRightBone
        val leftBone = AppTheme.semantic.handLeftBone
        val rightJoint = AppTheme.semantic.handRightJoint
        val leftJoint = AppTheme.semantic.handLeftJoint

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // 640x480 analysis resolution in portrait (aspect ratio 480:640 = 3:4)
            val camW = 480f
            val camH = 640f

            // Exact FILL_CENTER transformation matching PreviewView
            val scale = maxOf(canvasW / camW, canvasH / camH)
            val scaledW = camW * scale
            val scaledH = camH * scale
            val offsetX = (canvasW - scaledW) / 2f
            val offsetY = (canvasH - scaledH) / 2f

            for (hand in handLandmarks) {
                val pts = hand.points
                if (pts.size < 21) continue

                val boneColor = if (hand.isRightHand) rightBone else leftBone
                val jointColor = if (hand.isRightHand) rightJoint else leftJoint

                // Draw bones with round caps
                for (c in HAND_CONNECTIONS) {
                    val p1Raw = pts[c.first]
                    val p2Raw = pts[c.second]

                    val p1 = Offset(p1Raw.x * scaledW + offsetX, p1Raw.y * scaledH + offsetY)
                    val p2 = Offset(p2Raw.x * scaledW + offsetX, p2Raw.y * scaledH + offsetY)

                    drawLine(
                        color = boneColor,
                        start = p1,
                        end = p2,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw spatial joints with glowing aura
                for (p in pts) {
                    val center = Offset(p.x * scaledW + offsetX, p.y * scaledH + offsetY)

                    drawCircle(
                        color = jointColor,
                        radius = 6.5f,
                        center = center
                    )
                    drawCircle(
                        color = jointColor.copy(alpha = 0.35f),
                        radius = 13f,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )
                }
            }

            // Instrument overlay draw completion for TRACKPERF
            if (latestFrameId > 0L) {
                TrackingProfiler.onOverlayDrawn(latestFrameId)
            }
        }

        // 3. Top Cluster (Slim merged row: AppTopBar + ModeSelector + FloatingCameraToolbar)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTopBar(
                    title = "Unspoken",
                    fps = fps,
                    isDebugOpen = isDebugOpen,
                    isDark = isDark,
                    onToggleDebug = { isDebugOpen = !isDebugOpen },
                    onToggleTheme = onToggleTheme
                )

                FloatingCameraToolbar(
                    isFrontCamera = isFrontCamera,
                    isTorchOn = isTorchOn,
                    isPaused = isPaused,
                    onToggleCamera = onToggleCamera,
                    onToggleTorch = onToggleTorch,
                    onTogglePause = onTogglePause,
                    onOpenSettings = onOpenSettings
                )
            }

            // Mode Selector on translucent scrim
            ModeSelector(
                currentMode = currentMode,
                onModeSelected = onModeChange,
                modifier = Modifier.fillMaxWidth()
            )

            // Auto-hiding, dismissible ISL 5-of-26 Status Chip (Phase 6.5)
            AnimatedVisibility(
                visible = islBannerVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "ISL Info",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ISL model recognizes A, B, C, D and Space (5 of 26)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(
                            onClick = { islBannerVisible = false },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Debug Telemetry Overlay (if active)
        if (isDebugOpen) {
            DebugOverlay(
                debounceState = debounceState,
                delegateName = TrackingProfiler.activeDelegate,
                handLandmarks = handLandmarks,
                onClose = { isDebugOpen = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 130.dp, start = AppTheme.spacing.medium)
            )
        }

        // 5. Hand-Aware Responsive Bottom Deck (Phase 6 & Phase 7)
        HandAwareBottomBar(
            shouldCollapse = shouldCollapse,
            debounceState = debounceState,
            onPinExpanded = {
                coroutineScope.launch {
                    isPinnedExpanded = true
                    delay(5000L)
                    isPinnedExpanded = false
                }
            },
            onSpace = onSpace,
            onDelete = onBackspace,
            onClear = onClear,
            onSpeak = onSpeak,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 6. Camera Permission Required Screen
        if (!hasCameraPermission) {
            EmptyState(
                icon = Icons.Default.VideocamOff,
                title = "Camera Permission Required",
                description = "Unspoken needs camera access to track hands and recognize sign language gestures in real time.",
                actionLabel = "Grant Permission",
                onActionClick = onRequestCameraPermission,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 7. Neural Model Loading Indicator
        if (!assetStatus.isReady) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack.copy(alpha = 0.85f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ExpressiveLoading(message = assetStatus.message)
                }
            }
        }
    }
}
