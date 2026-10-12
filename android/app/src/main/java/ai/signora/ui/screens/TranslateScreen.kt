package ai.signora.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import ai.signora.models.*
import ai.signora.recovery.AutoAssetManager
import ai.signora.ui.components.*
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.PureBlack

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
    assetStatus: AutoAssetManager.AssetStatus,
    onModeChange: (RecognitionMode) -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleTheme: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSpace: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDebugOpen by remember { mutableStateOf(false) }

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

        // 2. Hand Skeleton Landmark Overlay (Zero hardcoded colors: pure AppTheme.semantic tokens)
        val rightBone = AppTheme.semantic.handRightBone
        val leftBone = AppTheme.semantic.handLeftBone
        val rightJoint = AppTheme.semantic.handRightJoint
        val leftJoint = AppTheme.semantic.handLeftJoint

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val connections = listOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 4,       // Thumb
                0 to 5, 5 to 6, 6 to 7, 7 to 8,       // Index
                0 to 9, 9 to 10, 10 to 11, 11 to 12,  // Middle
                0 to 13, 13 to 14, 14 to 15, 15 to 16,// Ring
                0 to 17, 17 to 18, 18 to 19, 19 to 20 // Pinky
            )

            for (hand in handLandmarks) {
                val pts = hand.points
                if (pts.size < 21) continue

                val boneColor = if (hand.isRightHand) rightBone else leftBone
                val jointColor = if (hand.isRightHand) rightJoint else leftJoint

                // Draw bones with round caps
                for ((start, end) in connections) {
                    val p1 = Offset(pts[start].x * canvasW, pts[start].y * canvasH)
                    val p2 = Offset(pts[end].x * canvasW, pts[end].y * canvasH)
                    drawLine(
                        color = boneColor,
                        start = p1,
                        end = p2,
                        strokeWidth = 4.5f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw spatial joints with glowing aura
                for (p in pts) {
                    val center = Offset(p.x * canvasW, p.y * canvasH)
                    drawCircle(
                        color = jointColor,
                        radius = 6f,
                        center = center
                    )
                    drawCircle(
                        color = jointColor.copy(alpha = 0.35f),
                        radius = 12f,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }

        // 3. Top Cluster (AppTopBar & ModeSelector)
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
                    onOpenSettings = {}
                )
            }

            // Mode Selector
            ModeSelector(
                currentMode = currentMode,
                onModeSelected = onModeChange,
                modifier = Modifier.fillMaxWidth()
            )

            // Honest ISL 5-of-26 Status Banner
            if (currentMode == RecognitionMode.ISL) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "ISL Info",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ISL model currently recognizes A, B, C, D and Space only (5 of 26)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // 4. Debug Telemetry Overlay (if active)
        if (isDebugOpen) {
            DebugOverlay(
                debounceState = debounceState,
                delegateName = "MediaPipe Vision + MLP",
                handLandmarks = handLandmarks,
                onClose = { isDebugOpen = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 130.dp, start = AppTheme.spacing.medium)
            )
        }

        // 5. Expressive Bottom Deck (Constrained to <= 38% height)
        Surface(
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Action Button Row: Space, Delete, Clear, Speak
                ActionButtonRow(
                    transcript = debounceState.transcript,
                    onSpace = onSpace,
                    onDelete = onBackspace,
                    onClear = onClear,
                    onSpeak = onSpeak,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

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
