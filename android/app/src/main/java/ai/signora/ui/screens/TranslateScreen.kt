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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import ai.signora.engine.HandPresenceDetector
import ai.signora.ml.BodySegmentation
import ai.signora.ml.BodySegmentZone
import ai.signora.ml.FeatureExtractor
import ai.signora.models.*
import ai.signora.nlp.EnglishToIslResult
import ai.signora.speech.SpeechState
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

private val UPPER_BODY_CONNECTIONS = listOf(
    11 to 12, // Shoulders
    11 to 13, 13 to 15, // Left Arm
    12 to 14, 14 to 16, // Right Arm
    11 to 23, 12 to 24, // Torso sides
    23 to 24 // Waist
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranslateScreen(
    previewView: PreviewView,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    debounceState: DebounceState,
    handLandmarks: List<HandLandmarks>,
    poseLandmarks: PoseLandmarks? = null,
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
    nlpState: NlpState = NlpState(),
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
    cameraResolution: Pair<Int, Int> = Pair(480, 640),
    bottomBarPadding: androidx.compose.ui.unit.Dp = 0.dp,
    speechState: SpeechState = SpeechState.Idle,
    speechPartialText: String = "",
    speechSoundLevel: Float = 0f,
    speechTranslationResult: EnglishToIslResult? = null,
    onStartSpeechListening: () -> Unit = {},
    onStopSpeechListening: () -> Unit = {},
    onManualTranslateText: (String) -> Unit = {},
    onAttachPreview: (PreviewView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isDebugOpen by remember { mutableStateOf(false) }
    var isSpeechToSignOpen by remember { mutableStateOf(false) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // 1. Fullscreen Camera Viewport
        AndroidView(
            factory = { _ ->
                (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                previewView.apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also {
                    onAttachPreview(it)
                }
            },
            update = { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                onAttachPreview(view)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Exact Landmark Overlay Alignment (Phase 3: FILL_CENTER scale/crop + front-camera horizontal mirror)
        val rightBone = AppTheme.semantic.handRightBone
        val leftBone = AppTheme.semantic.handLeftBone
        val rightJoint = AppTheme.semantic.handRightJoint
        val leftJoint = AppTheme.semantic.handLeftJoint

        val segmentTextPaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        }

        // Active anatomical body segment computation (supports simultaneous dual-hand multi-zone detection)
        val activeSegments = remember(handLandmarks, poseLandmarks) {
            BodySegmentation.detectActiveSegments(handLandmarks, poseLandmarks)
        }
        val activeSegment = activeSegments.firstOrNull()

        // Active anatomical anchor zone computation across detected hands
        val (activeAnchorZone, activeAnchorPt) = remember(handLandmarks, poseLandmarks, activeSegments) {
            if (activeSegments.size >= 2) {
                Pair("${activeSegments[0].shortTag} + ${activeSegments[1].shortTag}", activeSegments[0].center)
            } else if (activeSegments.size == 1) {
                Pair(activeSegments[0].id, activeSegments[0].center)
            } else if (handLandmarks.isNotEmpty() && poseLandmarks != null) {
                var zone = "OPEN_SPACE"
                var pt: Point3D? = null
                val zones = mutableListOf<String>()
                for (h in handLandmarks) {
                    val (z, p) = FeatureExtractor.detectBodyAnchor(h, poseLandmarks)
                    if (z != "OPEN_SPACE" && !zones.contains(z)) {
                        zones.add(z)
                        if (pt == null) pt = p
                    }
                }
                if (zones.size >= 2) {
                    Pair("${zones[0]} + ${zones[1]}", pt)
                } else if (zones.isNotEmpty()) {
                    Pair(zones[0], pt)
                } else Pair("OPEN_SPACE", null)
            } else Pair("OPEN_SPACE", null)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Dynamic analysis resolution in portrait
            val camW = cameraResolution.first.toFloat().coerceAtLeast(1f)
            val camH = cameraResolution.second.toFloat().coerceAtLeast(1f)

            // Exact FILL_CENTER transformation matching PreviewView
            val scale = maxOf(canvasW / camW, canvasH / camH)
            val scaledW = camW * scale
            val scaledH = camH * scale
            val offsetX = (canvasW - scaledW) / 2f
            val offsetY = (canvasH - scaledH) / 2f

            val toScreen: (Point3D) -> Offset = { p ->
                Offset(p.x * scaledW + offsetX, p.y * scaledH + offsetY)
            }

            // 1. Draw Upper Body Skeleton (Head, Neck, Shoulders, Arms, Spine, Waist)
            if (poseLandmarks != null && poseLandmarks.points.size >= 25) {
                val pts = poseLandmarks.points
                val boneColor = Color(0xFF818CF8).copy(alpha = 0.55f) // Electric Indigo
                val spineColor = Color(0xFF6366F1).copy(alpha = 0.45f)
                val jointRingColor = Color(0xFFA5B4FC).copy(alpha = 0.60f)
                val jointCoreColor = Color.White

                // Upper body bones
                for ((s, e) in UPPER_BODY_CONNECTIONS) {
                    if (s < pts.size && e < pts.size) {
                        drawLine(
                            color = boneColor,
                            start = toScreen(pts[s]),
                            end = toScreen(pts[e]),
                            strokeWidth = 3.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Head/Neck lines (Nose to Shoulders, Neck midpoint)
                if (pts.isNotEmpty()) {
                    val pNose = toScreen(pts[0])
                    if (11 < pts.size && 12 < pts.size) {
                        val pLsh = toScreen(pts[11])
                        val pRsh = toScreen(pts[12])
                        val pNeck = Offset((pLsh.x + pRsh.x) / 2f, (pLsh.y + pRsh.y) / 2f)
                        drawLine(boneColor.copy(alpha = 0.35f), pNose, pNeck, strokeWidth = 2.5f, cap = StrokeCap.Round)

                        // Central spine (Neck to Waist center)
                        if (23 < pts.size && 24 < pts.size) {
                            val pLhip = toScreen(pts[23])
                            val pRhip = toScreen(pts[24])
                            val pWaist = Offset((pLhip.x + pRhip.x) / 2f, (pLhip.y + pRhip.y) / 2f)
                            drawLine(spineColor, pNeck, pWaist, strokeWidth = 3.0f, cap = StrokeCap.Round)
                        }
                    }
                }

                // Key Body Joints: Nose, Shoulders, Elbows, Wrists, Hips
                val keyJoints = intArrayOf(0, 11, 12, 13, 14, 15, 16, 23, 24)
                for (idx in keyJoints) {
                    if (idx < pts.size) {
                        val center = toScreen(pts[idx])
                        drawCircle(color = jointRingColor, radius = 5.0f, center = center)
                        drawCircle(color = jointCoreColor, radius = 2.0f, center = center)
                    }
                }

                // Anatomical Body Part Mapping Zones (Visual feedback for chest, chin, head, throat anchors)
                val segments = BodySegmentation.computeSegments(poseLandmarks)
                for (seg in segments) {
                    val center = toScreen(seg.center)
                    val w = seg.width * scaledW
                    val h = seg.height * scaledH
                    val left = center.x - w / 2f
                    val top = center.y - h / 2f

                    val isActive = activeSegments.any { it.id == seg.id }
                    if (isActive) {
                        // Vibrant glowing active anchor zone
                        drawRoundRect(
                            color = Color(0xFF10B981).copy(alpha = 0.25f),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(16f, 16f)
                        )
                        drawRoundRect(
                            color = Color(0xFF10B981),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(16f, 16f),
                            style = Stroke(width = 3.0f)
                        )
                        segmentTextPaint.color = Color(0xFF10B981).toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            "▶ ${seg.shortTag} ◀",
                            center.x,
                            top + 22f,
                            segmentTextPaint
                        )
                    } else {
                        // Subtle unobtrusive boundary showing mapped zones
                        drawRoundRect(
                            color = Color(0xFF818CF8).copy(alpha = 0.22f),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(14f, 14f),
                            style = Stroke(
                                width = 1.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        )
                    }
                }
            }

            // 2. Draw Hand Skeletons
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
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw crisp spatial joints
                for (p in pts) {
                    val center = Offset(p.x * scaledW + offsetX, p.y * scaledH + offsetY)

                    drawCircle(
                        color = jointColor,
                        radius = 4.5f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 1.8f,
                        center = center
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
                    title = "Signora",
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
                    onOpenSettings = onOpenSettings,
                    onOpenSpeechToSign = { isSpeechToSignOpen = true }
                )
            }

            // Mode Selector on translucent scrim
            ModeSelector(
                currentMode = currentMode,
                onModeSelected = onModeChange,
                modifier = Modifier.fillMaxWidth()
            )

            // Prominent Real-time Candidate Floating Badge
            AnimatedVisibility(
                visible = debounceState.candidate.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = ai.signora.engine.DebounceEngine.formatDisplayText(debounceState.candidate),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                        if (activeAnchorZone != "OPEN_SPACE") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "⚓ $activeAnchorZone",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${(debounceState.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        CircularProgressIndicator(
                            progress = { debounceState.dwellProgress },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Real-time Anatomical Body Map Active Anchor Pill
            AnimatedVisibility(
                visible = activeAnchorZone != "OPEN_SPACE",
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.88f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Text(
                            text = "BODY MAP: $activeAnchorZone",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                        )
                    }
                }
            }
        }

        // 4. Live Gloss Token Chips (ISL Mode only) — sleek compact pill
        if (currentMode == RecognitionMode.ISL && nlpState.pendingGlosses.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomBarPadding + 195.dp)
                    .padding(horizontal = AppTheme.spacing.medium)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GLOSS:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    nlpState.pendingGlosses.takeLast(4).forEach { gloss ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = ai.signora.engine.DebounceEngine.formatDisplayText(gloss),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Debug Telemetry Overlay (if active)
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

        // 6. Responsive Bottom Deck with Live Translation
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
            synthesizedSentence = nlpState.synthesizedSentence,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarPadding)
        )

        // 7. Camera Permission Required Screen
        if (!hasCameraPermission) {
            EmptyState(
                icon = Icons.Default.VideocamOff,
                title = "Camera Permission Required",
                description = "Signora needs camera access to track hands and recognize sign language gestures in real time.",
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

        // 8. Bidirectional Speech-to-Sign Bottom Sheet (Hearing Person Speaks)
        if (isSpeechToSignOpen) {
            SpeechToSignBottomSheet(
                speechState = speechState,
                partialText = speechPartialText,
                soundLevel = speechSoundLevel,
                translationResult = speechTranslationResult,
                onStartListening = onStartSpeechListening,
                onStopListening = onStopSpeechListening,
                onManualTranslate = onManualTranslateText,
                onDismiss = {
                    onStopSpeechListening()
                    isSpeechToSignOpen = false
                }
            )
        }
    }
}
