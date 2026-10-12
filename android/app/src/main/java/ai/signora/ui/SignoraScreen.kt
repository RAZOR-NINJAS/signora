package ai.signora.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.signora.models.*
import ai.signora.recovery.AutoAssetManager
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignoraScreen(
    previewView: PreviewView,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    debounceState: DebounceState,
    handLandmarks: List<HandLandmarks>,
    currentMode: RecognitionMode,
    dwellProfile: DwellProfile,
    isMuted: Boolean,
    isFrontCamera: Boolean,
    assetStatus: AutoAssetManager.AssetStatus,
    onModeChange: (RecognitionMode) -> Unit,
    onDwellProfileChange: (DwellProfile) -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSpace: () -> Unit,
    onSpeak: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showModeSelection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // 1. CameraX Live Viewport
        AndroidView(
            factory = { _ ->
                (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Material 3 Expressive Hand Skeleton Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            for (hand in handLandmarks) {
                val pts = hand.points
                if (pts.size < 21) continue

                val isRight = hand.isRightHand
                // Expressive dual-tone hand palette: Cyan/Mint for Right Hand, Magenta/Coral for Left Hand
                val boneColor = if (isRight) Cyan400.copy(alpha = 0.80f) else Magenta400.copy(alpha = 0.75f)
                val jointColor = if (isRight) Emerald400 else Amber400

                val connections = listOf(
                    0 to 1, 1 to 2, 2 to 3, 3 to 4,       // Thumb
                    0 to 5, 5 to 6, 6 to 7, 7 to 8,       // Index
                    0 to 9, 9 to 10, 10 to 11, 11 to 12,  // Middle
                    0 to 13, 13 to 14, 14 to 15, 15 to 16,// Ring
                    0 to 17, 17 to 18, 18 to 19, 19 to 20 // Pinky
                )

                // Bones
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

                // Precision Spatial Joints with glowing aura
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
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                    )
                }
            }
        }

        // 3. Material 3 Expressive Top App Bar & Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Name & Codename Signora Badge & Mode Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = SurfaceContainer.copy(alpha = 0.92f),
                    modifier = Modifier.border(1.dp, ExpressiveBorder, RoundedCornerShape(32.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "UNSPOKEN",
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

                        // Codename: Signora Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ExpressivePrimary.copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CODENAME: SIGNORA",
                                color = ExpressivePrimaryLight,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                // Recognition Mode Switcher Pill (ASL / ISL / Fingerspelling)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (currentMode == RecognitionMode.ISL) ExpressiveSecondaryContainer else ExpressivePrimaryContainer,
                    modifier = Modifier
                        .border(1.dp, ExpressiveBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            showModeSelection = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (currentMode == RecognitionMode.ISL) ExpressiveSecondaryLight else ExpressivePrimaryLight)
                        )
                        Text(
                            text = currentMode.displayName,
                            color = PureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right Quick Actions & Status Cluster
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Real-time FPS Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .border(1.dp, ExpressiveBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (debounceState.fps >= 25f) ExpressiveEmerald else ExpressiveAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${debounceState.fps.toInt()} FPS",
                            color = Slate100,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Material 3 Expressive HorizontalFloatingToolbar for Quick Actions
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = SurfaceContainer.copy(alpha = 0.92f)
                    ),
                    modifier = Modifier.border(1.dp, ExpressiveBorder, CircleShape)
                ) {
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Toggle Audio",
                            tint = if (isMuted) Slate500 else ExpressivePrimaryLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onToggleCamera) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = Slate100,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = Slate100,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 4. Center Material 3 Expressive Dwell HUD Target
        AnimatedVisibility(
            visible = debounceState.candidate.isNotBlank() && debounceState.dwellProgress > 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SurfaceDark.copy(alpha = 0.85f),
                modifier = Modifier
                    .border(2.dp, ExpressivePrimary.copy(alpha = 0.40f), RoundedCornerShape(28.dp))
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(116.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Material 3 Expressive Circular Wavy Progress Indicator
                        CircularWavyProgressIndicator(
                            progress = { debounceState.dwellProgress },
                            modifier = Modifier.size(116.dp),
                            color = ExpressivePrimary,
                            trackColor = ExpressivePrimary.copy(alpha = 0.15f)
                        )
                        // Candidate Letter
                        Text(
                            text = debounceState.candidate,
                            color = PureWhite,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Dwell Countdown Pill Badge
                    val elapsedSeconds = debounceState.dwellProgress * (dwellProfile.dwellMs / 1000f)
                    val formattedElapsed = String.format(Locale.US, "%.2fs", elapsedSeconds)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ExpressivePrimaryContainer.copy(alpha = 0.70f),
                        modifier = Modifier.border(1.dp, ExpressiveBorder, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "$formattedElapsed / ${dwellProfile.label}",
                            color = ExpressivePrimaryLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 5. Material 3 Expressive Command Deck (Bottom Bar)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = SurfaceElevated.copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ExpressiveBorder, RoundedCornerShape(32.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Sign Detection Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (debounceState.candidate.isNotBlank()) ExpressiveEmerald else Slate500)
                                )
                                Text(
                                    text = "ACTIVE SIGN",
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (debounceState.candidate.isNotBlank()) debounceState.candidate else "READY",
                                color = PureWhite,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (debounceState.candidate.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = ExpressivePrimaryContainer,
                                    modifier = Modifier.border(1.dp, ExpressiveBorder, RoundedCornerShape(16.dp))
                                ) {
                                    Text(
                                        text = "${(debounceState.confidence * 100).toInt()}% CONF",
                                        color = ExpressivePrimaryLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Dwell Profile Badge Pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceContainerHigh,
                                modifier = Modifier.border(1.dp, ExpressiveBorder, RoundedCornerShape(16.dp))
                            ) {
                                Text(
                                    text = dwellProfile.label,
                                    color = Slate100,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Smooth Dwell Progress Bar with Timing Labels
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (debounceState.dwellProgress > 0f) "HOLDING..." else "DWELL TIMER",
                            color = if (debounceState.dwellProgress > 0f) ExpressivePrimaryLight else Slate500,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${(debounceState.dwellProgress * 100).toInt()}%",
                            color = Slate400,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearWavyProgressIndicator(
                        progress = { debounceState.dwellProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = ExpressivePrimary,
                        trackColor = SurfaceContainerHigh
                    )

                    // Transcript Text Box
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceDark.copy(alpha = 0.88f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ExpressiveBorder, RoundedCornerShape(18.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (debounceState.transcript.isNotBlank()) debounceState.transcript else "Hold sign gesture steadily to transcribe text...",
                                color = if (debounceState.transcript.isNotBlank()) Slate100 else Slate500,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Expressive Action Buttons (Material 3 Expressive Tonal Pills)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onSpace,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SurfaceContainerHigh,
                                contentColor = Slate100
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SpaceBar, contentDescription = "Space", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SPACE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = onBackspace,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SurfaceContainerHigh,
                                contentColor = Slate300
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = onClear,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ExpressiveTertiary.copy(alpha = 0.25f),
                                contentColor = ExpressiveTertiaryLight
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CLEAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onSpeak,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExpressivePrimary,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("SPEAK", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // 6. Material 3 Expressive Settings Dialog
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = SurfaceElevated,
                shape = RoundedCornerShape(28.dp),
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = ExpressivePrimaryLight,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                "Settings & Preferences",
                                color = PureWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            "Unspoken • Material 3 Expressive UI",
                            color = ExpressivePrimaryLight,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "DWELL DURATION PROFILE",
                            color = Slate400,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        // Material 3 Expressive Segmented Buttons for quick Dwell switching
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DwellProfile.values().forEachIndexed { index, profile ->
                                SegmentedButton(
                                    selected = dwellProfile == profile,
                                    onClick = { onDwellProfileChange(profile) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = DwellProfile.values().size
                                    ),
                                    label = {
                                        Text(
                                            profile.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }

                        // Material 3 Expressive Selectable Cards for Dwell Profiles
                        DwellProfile.values().forEach { profile ->
                            val isSelected = dwellProfile == profile
                            val profileTitle = when (profile) {
                                DwellProfile.QUICK -> "Quick"
                                DwellProfile.STANDARD -> "Standard"
                                DwellProfile.STEADY -> "Steady"
                            }
                            val tagText = when (profile) {
                                DwellProfile.QUICK -> "⚡ FAST"
                                DwellProfile.STANDARD -> "★ RECOMMENDED"
                                DwellProfile.STEADY -> "🛡 PRECISE"
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) ExpressivePrimaryContainer.copy(alpha = 0.55f) else SurfaceContainer
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) ExpressivePrimary else ExpressiveBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDwellProfileChange(profile) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onDwellProfileChange(profile) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = ExpressivePrimary,
                                                unselectedColor = Slate500
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "$profileTitle (${profile.label})",
                                                    color = PureWhite,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = profile.subtitle,
                                                color = Slate400,
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) ExpressivePrimary.copy(alpha = 0.30f) else SurfaceContainerHigh,
                                        modifier = Modifier.padding(start = 6.dp)
                                    ) {
                                        Text(
                                            text = tagText,
                                            color = if (isSelected) ExpressivePrimaryLight else Slate300,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = SurfaceContainerHigh,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Neural Speech Synthesizer Toggle Row
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ExpressiveBorder, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (!isMuted) Icons.Filled.RecordVoiceOver else Icons.Filled.VoiceOverOff,
                                        contentDescription = null,
                                        tint = if (!isMuted) ExpressiveEmerald else Slate500,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text("Neural TTS Vocalizer", color = PureWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text(if (!isMuted) "Auto-speak confirmed signs" else "Audio playback muted", color = Slate400, fontSize = 11.sp)
                                    }
                                }
                                Switch(
                                    checked = !isMuted,
                                    onCheckedChange = { onToggleMute() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PureWhite,
                                        checkedTrackColor = ExpressivePrimary,
                                        uncheckedThumbColor = Slate500,
                                        uncheckedTrackColor = SurfaceContainerHigh
                                    )
                                )
                            }
                        }

                        // Active Camera Lens Switch Row
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ExpressiveBorder, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlipCameraAndroid,
                                        contentDescription = null,
                                        tint = ExpressiveSecondaryLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text("Active Camera Lens", color = PureWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text(if (isFrontCamera) "Front (Selfie mirror)" else "Back (Rear lens)", color = Slate400, fontSize = 11.sp)
                                    }
                                }
                                OutlinedButton(
                                    onClick = onToggleCamera,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, ExpressiveBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isFrontCamera) "FLIP TO BACK" else "FLIP TO FRONT",
                                        color = ExpressivePrimaryLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSettings = false },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ExpressivePrimary,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("DONE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        // 7. Camera Permission Overlay Banner
        if (!hasCameraPermission) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDark.copy(alpha = 0.95f)),
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                        modifier = Modifier
                            .padding(24.dp)
                            .border(1.dp, ExpressiveBorder, RoundedCornerShape(28.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Camera Permission",
                                tint = ExpressiveAmber,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Camera Permission Required",
                                color = PureWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Unspoken requires camera access to recognize hand signs and gestures in real time.",
                                color = Slate400,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onRequestCameraPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ExpressivePrimary,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = "Grant Camera Permission",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Material 3 Expressive Mode Selection Dialog / Screen
        if (showModeSelection) {
            ModeSelectionScreen(
                currentMode = currentMode,
                assetStatus = assetStatus,
                onModeChange = { newMode ->
                    onModeChange(newMode)
                },
                onDismiss = { showModeSelection = false }
            )
        }

        // 9. Self-Healing Model Downloader Banner
        if (!assetStatus.isReady) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDark.copy(alpha = 0.90f)),
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                        modifier = Modifier
                            .padding(32.dp)
                            .border(1.dp, ExpressiveBorder, RoundedCornerShape(28.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LoadingIndicator(
                                color = ExpressivePrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Verifying Neural Models",
                                color = PureWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = assetStatus.message,
                                color = Slate400,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModeSelectionScreen(
    currentMode: RecognitionMode,
    assetStatus: AutoAssetManager.AssetStatus,
    onModeChange: (RecognitionMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = ExpressivePrimaryLight,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Recognition Mode",
                        color = PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Material 3 Expressive Mode Selector",
                    color = ExpressivePrimaryLight,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SELECT ACTIVE SIGN LANGUAGE MODEL",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // Material 3 Expressive toggle ButtonGroup
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    toggleableItem(
                        checked = currentMode == RecognitionMode.ASL,
                        label = "ASL",
                        onCheckedChange = { if (it) onModeChange(RecognitionMode.ASL) }
                    )
                    toggleableItem(
                        checked = currentMode == RecognitionMode.ISL,
                        label = "ISL",
                        onCheckedChange = { if (it) onModeChange(RecognitionMode.ISL) }
                    )
                    toggleableItem(
                        checked = currentMode == RecognitionMode.FINGERSPELLING,
                        label = "Fingerspelling",
                        onCheckedChange = { if (it) onModeChange(RecognitionMode.FINGERSPELLING) }
                    )
                }

                // Model Loading / Verification State using Expressive LoadingIndicator
                if (!assetStatus.isReady) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ExpressiveBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LoadingIndicator(
                                color = ExpressivePrimary,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading Neural Model...",
                                color = PureWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = assetStatus.message,
                                color = Slate400,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ExpressiveBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ExpressiveEmerald)
                                )
                                Text(
                                    text = currentMode.displayName,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentMode.description,
                                color = Slate400,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExpressivePrimary,
                    contentColor = PureWhite
                )
            ) {
                Text("DONE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}
