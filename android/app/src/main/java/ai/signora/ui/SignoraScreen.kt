package ai.signora.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.signora.models.*
import ai.signora.recovery.AutoAssetManager

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

            // Draw hand skeleton with Material 3 Expressive colors
            for (hand in handLandmarks) {
                val pts = hand.points
                if (pts.size < 21) continue

                val isRight = hand.isRightHand
                // Material 3 Expressive palette: Cyan/Mint for Right Hand, Coral/Amber for Left Hand
                val boneColor = if (isRight) Cyan400.copy(alpha = 0.75f) else Magenta400.copy(alpha = 0.70f)
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
                        radius = 11f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                    )
                }
            }
        }

        // 3. Material 3 Expressive Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Name & Signora Engine Badge & Mode Switcher
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

                        // Codename Signora chip
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

                // Mode Switcher Pill (ASL / ISL)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (currentMode == RecognitionMode.ISL) ExpressiveSecondaryContainer else ExpressivePrimaryContainer,
                    modifier = Modifier
                        .border(1.dp, ExpressiveBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            val nextMode = if (currentMode == RecognitionMode.ASL) RecognitionMode.ISL else RecognitionMode.ASL
                            onModeChange(nextMode)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
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

            // Right Utilities (FPS, Audio, Flip, Settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // FPS Pill
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

                // Mute / Unmute
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer.copy(alpha = 0.92f))
                        .border(1.dp, ExpressiveBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = "Toggle Audio",
                        tint = if (isMuted) Slate500 else ExpressivePrimaryLight,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Camera Flip
                IconButton(
                    onClick = onToggleCamera,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer.copy(alpha = 0.92f))
                        .border(1.dp, ExpressiveBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = Slate100,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer.copy(alpha = 0.92f))
                        .border(1.dp, ExpressiveBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Slate100,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 4. Center Expressive Dwell Circle Target
        if (debounceState.candidate.isNotBlank() && debounceState.dwellProgress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark.copy(alpha = 0.70f))
                    .border(2.dp, ExpressiveBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { debounceState.dwellProgress },
                    modifier = Modifier.size(96.dp),
                    color = ExpressivePrimary,
                    strokeWidth = 5.dp,
                    trackColor = ExpressivePrimary.copy(alpha = 0.15f)
                )
                Text(
                    text = debounceState.candidate,
                    color = PureWhite,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
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
                            Text(
                                text = "ACTIVE SIGN",
                                color = Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (debounceState.candidate.isNotBlank()) debounceState.candidate else "READY",
                                color = PureWhite,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

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
                    }

                    // Smooth Dwell Progress Line
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { debounceState.dwellProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
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
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (debounceState.transcript.isNotBlank()) debounceState.transcript else "Hold sign gesture in camera view to transcribe...",
                                color = if (debounceState.transcript.isNotBlank()) Slate100 else Slate500,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Expressive Action Buttons
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSpace,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ExpressiveBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SPACE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onBackspace,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ExpressiveBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onClear,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpressiveTertiaryLight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ExpressiveBorder),
                            modifier = Modifier.weight(1f)
                        ) {
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
                            Icon(Icons.Filled.VolumeUp, contentDescription = "Speak", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
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
                        Text(
                            "Unspoken Settings",
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Codename: Signora • v0.2.0",
                            color = ExpressivePrimaryLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            "Dwell Hold Time Profile",
                            color = Slate400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DwellProfile.values().forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDwellProfileChange(profile)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = dwellProfile == profile,
                                    onClick = { onDwellProfileChange(profile) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = ExpressivePrimary,
                                        unselectedColor = Slate500
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(profile.label, color = PureWhite, fontSize = 14.sp)
                            }
                        }

                        HorizontalDivider(color = SurfaceContainerHigh, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Neural Speech Synthesizer", color = PureWhite, fontSize = 14.sp)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active Camera Lens", color = PureWhite, fontSize = 14.sp)
                            TextButton(onClick = onToggleCamera) {
                                Text(
                                    text = if (isFrontCamera) "Front (Selfie)" else "Back (Lens)",
                                    color = ExpressivePrimaryLight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("DONE", color = ExpressivePrimaryLight, fontWeight = FontWeight.Bold)
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

        // 8. Self-Healing Model Downloader Banner
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
                            CircularProgressIndicator(color = ExpressivePrimary)
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
