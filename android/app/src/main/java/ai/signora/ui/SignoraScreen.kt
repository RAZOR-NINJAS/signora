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
            .background(Slate950)
    ) {
        // 1. CameraX Live Viewport
        AndroidView(
            factory = { _ ->
                (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Cybernetic Hand Skeleton Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            for (hand in handLandmarks) {
                val pts = hand.points
                if (pts.size < 21) continue

                val color = if (hand.isRightHand) Cyan400 else Magenta400

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
                        color = color.copy(alpha = 0.8f),
                        start = p1,
                        end = p2,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }

                // Joints
                for (p in pts) {
                    val center = Offset(p.x * canvasW, p.y * canvasH)
                    drawCircle(
                        color = Color.White,
                        radius = 7f,
                        center = center
                    )
                    drawCircle(
                        color = color,
                        radius = 11f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }
        }

        // 3. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Selector Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Slate900.copy(alpha = 0.85f),
                modifier = Modifier
                    .border(1.dp, Cyan400.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .clickable {
                        val nextMode = if (currentMode == RecognitionMode.ASL) RecognitionMode.ISL else RecognitionMode.ASL
                        onModeChange(nextMode)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (currentMode == RecognitionMode.ASL) Cyan400 else Magenta400)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentMode.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right utility icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // FPS Counter
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate900.copy(alpha = 0.85f),
                    modifier = Modifier
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${debounceState.fps.toInt()} FPS",
                        color = Emerald400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Mute / Unmute
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Slate900.copy(alpha = 0.85f))
                        .border(1.dp, Slate800, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Toggle Audio",
                        tint = if (isMuted) Color.Gray else Cyan400,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Camera Flip
                IconButton(
                    onClick = onToggleCamera,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Slate900.copy(alpha = 0.85f))
                        .border(1.dp, Slate800, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = Cyan400,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Settings
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Slate900.copy(alpha = 0.85f))
                        .border(1.dp, Slate800, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 4. Center Dwell Target Indicator
        if (debounceState.candidate.isNotBlank() && debounceState.dwellProgress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Slate900.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { debounceState.dwellProgress },
                    modifier = Modifier.size(80.dp),
                    color = if (debounceState.confirmed) Emerald400 else Cyan400,
                    strokeWidth = 6.dp,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    text = debounceState.candidate,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // 5. Bottom Glassmorphic Command Deck
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Slate900.copy(alpha = 0.90f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Cyan400.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Candidate Recognition Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DETECTED SIGN",
                                color = Cyan400.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (debounceState.candidate.isNotBlank()) debounceState.candidate else "READY",
                                color = if (debounceState.confirmed) Emerald400 else Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        if (debounceState.candidate.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate800,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = "${(debounceState.confidence * 100).toInt()}% CONF",
                                    color = Emerald400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Linear Dwell Progress Bar
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { debounceState.dwellProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (debounceState.confirmed) Emerald400 else Cyan400,
                        trackColor = Slate800
                    )

                    // Transcript Text Display
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate950.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (debounceState.transcript.isNotBlank()) debounceState.transcript else "Hold sign gesture in view to transcribe...",
                                color = if (debounceState.transcript.isNotBlank()) Color.White else Color.Gray,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Action Controls
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = onSpace,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SPACE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = onBackspace,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("UNDO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = onClear,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CLEAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onSpeak,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Speak", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 6. Settings Modal Sheet
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = Slate900,
                title = {
                    Text("Signora Settings & Preferences", color = Cyan400, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("Recognition Dwell Timing Profile", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
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
                                    colors = RadioButtonDefaults.colors(selectedColor = Cyan400)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(profile.label, color = Color.White, fontSize = 14.sp)
                            }
                        }

                        HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Neural Speech Audio", color = Color.White, fontSize = 14.sp)
                            Switch(
                                checked = !isMuted,
                                onCheckedChange = { onToggleMute() },
                                colors = SwitchDefaults.colors(checkedThumbColor = Cyan400)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Camera Orientation", color = Color.White, fontSize = 14.sp)
                            TextButton(onClick = onToggleCamera) {
                                Text(if (isFrontCamera) "Front (Selfie)" else "Back (Rear)", color = Cyan400)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("DONE", color = Cyan400, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // 7. Camera Permission Overlay Banner
        if (!hasCameraPermission) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate950.copy(alpha = 0.95f)),
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        modifier = Modifier
                            .padding(24.dp)
                            .border(1.dp, Cyan400, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Camera Permission",
                                tint = Cyan400,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Camera Permission Required",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Signora needs camera access to detect and translate sign language in real time.",
                                color = Slate300,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onRequestCameraPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Grant Camera Permission",
                                    color = Slate950,
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
                    .background(Color.Black.copy(alpha = 0.85f)),
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        modifier = Modifier
                            .padding(32.dp)
                            .border(1.dp, Cyan400, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Cyan400)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Self-Healing System",
                                color = Cyan400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = assetStatus.message,
                                color = Color.White,
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
