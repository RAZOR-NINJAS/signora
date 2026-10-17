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
import ai.signora.engine.HandPresenceDetector
import ai.signora.ml.FeatureExtractor
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

private val UPPER_BODY_CONNECTIONS = listOf(
    11 to 12, // Shoulders
    11 to 13, 13 to 15, // Left Arm
    12 to 14, 14 to 16, // Right Arm
    11 to 23, 12 to 24, // Torso sides
    23 to 24 // Waist
)

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

        // Active anatomical anchor zone computation across detected hands
        val (activeAnchorZone, activeAnchorPt) = remember(handLandmarks, poseLandmarks) {
            if (handLandmarks.isNotEmpty() && poseLandmarks != null) {
                var zone = "OPEN_SPACE"
                var pt: Point3D? = null
                for (h in handLandmarks) {
                    val (z, p) = FeatureExtractor.detectBodyAnchor(h, poseLandmarks)
                    if (z != "OPEN_SPACE") {
                        zone = z
                        pt = p
                        break
                    }
                }
                Pair(zone, pt)
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

            // 1. Draw Upper Body Skeleton & Anatomical Body Map Anchors
            if (poseLandmarks != null && poseLandmarks.points.size >= 25) {
                val pts = poseLandmarks.points
                val boneColor = Color(0xFF6366F1).copy(alpha = 0.65f) // Electric Indigo
                val jointColor = Color(0xFFA5B4FC) // Soft Violet glow

                // Draw Upper Body Bones
                for ((s, e) in UPPER_BODY_CONNECTIONS) {
                    if (s < pts.size && e < pts.size) {
                        drawLine(
                            color = boneColor,
                            start = toScreen(pts[s]),
                            end = toScreen(pts[e]),
                            strokeWidth = 4.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Head/Neck lines (Nose to Shoulders)
                if (pts.isNotEmpty()) {
                    val pNose = toScreen(pts[0])
                    if (11 < pts.size) drawLine(boneColor.copy(alpha = 0.40f), pNose, toScreen(pts[11]), strokeWidth = 3f, cap = StrokeCap.Round)
                    if (12 < pts.size) drawLine(boneColor.copy(alpha = 0.40f), pNose, toScreen(pts[12]), strokeWidth = 3f, cap = StrokeCap.Round)
                }

                // Key Body Joints
                val keyJoints = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24)
                for (idx in keyJoints) {
                    if (idx < pts.size) {
                        val center = toScreen(pts[idx])
                        drawCircle(color = jointColor.copy(alpha = 0.35f), radius = 9f, center = center)
                        drawCircle(color = jointColor, radius = 5.5f, center = center)
                    }
                }

                // Anatomical Body Map Zones (Anchors)
                val pNose = pts[0]
                val pMl = pts[9]
                val pMr = pts[10]
                val pLsh = pts[11]
                val pRsh = pts[12]
                val pLhip = pts[23]
                val pRhip = pts[24]
                val pLwrist = pts[15]
                val pRwrist = pts[16]

                val shoulderDist = kotlin.math.sqrt((pLsh.x - pRsh.x) * (pLsh.x - pRsh.x) + (pLsh.y - pRsh.y) * (pLsh.y - pRsh.y))
                val sScale = if (shoulderDist > 0.15f) shoulderDist else 0.15f

                val mouthCenter = Point3D((pMl.x + pMr.x) / 2f, (pMl.y + pMr.y) / 2f, (pMl.z + pMr.z) / 2f)
                val crown = Point3D(pNose.x, pNose.y - 0.45f * sScale, pNose.z)
                val forehead = Point3D(pNose.x, pNose.y - 0.22f * sScale, pNose.z)
                val chin = Point3D(mouthCenter.x, mouthCenter.y + 0.12f * sScale, mouthCenter.z)
                val throat = Point3D(mouthCenter.x, mouthCenter.y + 0.30f * sScale, mouthCenter.z)
                val chestCenter = Point3D((pLsh.x + pRsh.x) / 2f, (pLsh.y + pRsh.y) / 2f, (pLsh.z + pRsh.z) / 2f)
                val chestLeft = Point3D(pLsh.x * 0.38f + chestCenter.x * 0.62f, chestCenter.y, chestCenter.z)
                val chestRight = Point3D(pRsh.x * 0.38f + chestCenter.x * 0.62f, chestCenter.y, chestCenter.z)
                val waistCenter = Point3D((pLhip.x + pRhip.x) / 2f, (pLhip.y + pRhip.y) / 2f, (pLhip.z + pRhip.z) / 2f)
                val midTorso = Point3D(chestCenter.x, (chestCenter.y + waistCenter.y) / 2f, chestCenter.z)
                val templeR = Point3D(pRsh.x * 0.4f + pNose.x * 0.6f, forehead.y, pNose.z)
                val templeL = Point3D(pLsh.x * 0.4f + pNose.x * 0.6f, forehead.y, pNose.z)

                val anchors = listOf(
                    Triple("CROWN", crown, 22f),
                    Triple("FOREHEAD", forehead, 22f),
                    Triple("TEMPLE", templeR, 20f),
                    Triple("TEMPLE", templeL, 20f),
                    Triple("CHIN", chin, 20f),
                    Triple("MOUTH_LIPS", mouthCenter, 20f),
                    Triple("THROAT", throat, 20f),
                    Triple("CHEST", chestCenter, 28f),
                    Triple("CHEST_LEFT", chestLeft, 22f),
                    Triple("CHEST_RIGHT", chestRight, 22f),
                    Triple("MID_TORSO", midTorso, 24f),
                    Triple("WAIST", waistCenter, 26f),
                    Triple("WRIST", pLwrist, 18f),
                    Triple("WRIST", pRwrist, 18f)
                )

                val emeraldColor = Color(0xFF10B981)
                val indigoColor = Color(0xFF818CF8)

                for ((name, pt, baseRadius) in anchors) {
                    val center = toScreen(pt)
                    val isAnchorActive = (name == activeAnchorZone) ||
                        (name == "CHEST" && (activeAnchorZone == "CHEST" || activeAnchorZone == "CHEST_CENTER")) ||
                        (name == "CHEST_LEFT" && activeAnchorZone == "HEART") ||
                        (name == "MID_TORSO" && activeAnchorZone == "STOMACH")

                    if (isAnchorActive) {
                        // High-tech Glowing Active Target Reticle
                        drawCircle(
                            color = emeraldColor.copy(alpha = 0.28f),
                            radius = baseRadius * 1.8f,
                            center = center
                        )
                        drawCircle(
                            color = emeraldColor,
                            radius = baseRadius * 1.3f,
                            center = center,
                            style = Stroke(
                                width = 3.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        )
                        drawCircle(
                            color = emeraldColor,
                            radius = baseRadius * 0.6f,
                            center = center,
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = emeraldColor,
                            radius = 4.5f,
                            center = center
                        )

                        // Crosshairs
                        val tick = 10f
                        val cx = center.x
                        val cy = center.y
                        val reach = baseRadius * 1.6f
                        drawLine(color = emeraldColor, start = Offset(cx - reach, cy), end = Offset(cx - reach + tick, cy), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        drawLine(color = emeraldColor, start = Offset(cx + reach - tick, cy), end = Offset(cx + reach, cy), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        drawLine(color = emeraldColor, start = Offset(cx, cy - reach), end = Offset(cx, cy - reach + tick), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        drawLine(color = emeraldColor, start = Offset(cx, cy + reach - tick), end = Offset(cx, cy + reach), strokeWidth = 2.5f, cap = StrokeCap.Round)
                    } else if (currentMode == RecognitionMode.ISL) {
                        // Subtle Anatomical Anchor Map Nodes
                        drawCircle(
                            color = indigoColor.copy(alpha = 0.18f),
                            radius = baseRadius,
                            center = center
                        )
                        drawCircle(
                            color = indigoColor.copy(alpha = 0.55f),
                            radius = baseRadius * 0.85f,
                            center = center,
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        )
                        drawCircle(
                            color = indigoColor.copy(alpha = 0.70f),
                            radius = 3.5f,
                            center = center
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
                                text = "ISL model recognizes 17 signs (A-F, Namaste, Hello, Help, Doctor, Emergency, Peace, Space, etc.)",
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
                            text = debounceState.candidate,
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

            // Real-time Anatomical Body Map Active Anchor Pill (when candidate is accumulating)
            AnimatedVisibility(
                visible = activeAnchorZone != "OPEN_SPACE" && debounceState.candidate.isBlank(),
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarPadding)
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
