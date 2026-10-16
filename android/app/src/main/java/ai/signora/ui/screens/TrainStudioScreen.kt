package ai.signora.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.signora.ml.FeatureExtractor
import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import ai.signora.models.PoseLandmarks
import ai.signora.models.SignInfo
import ai.signora.training.IslVocabulary
import ai.signora.training.TrainingDatasetManager
import ai.signora.ui.components.SpatialOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class StudioState {
    IDLE,
    COUNTDOWN,
    RECORDING,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainStudioScreen(
    previewView: PreviewView,
    hands: List<HandLandmarks>,
    pose: PoseLandmarks?,
    onToggleCamera: () -> Unit,
    cameraResolution: Pair<Int, Int> = Pair(480, 640),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val datasetManager = remember { TrainingDatasetManager(context) }

    // Vocabulary & Selection
    var selectedTab by remember { mutableStateOf(0) } // 0: Letters, 1: 300 Words
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var currentSignIndex by remember { mutableStateOf(0) }

    // Recorded counts cache
    var recordedCounts by remember { mutableStateOf(datasetManager.getRecordedCounts()) }

    // Studio state
    var studioState by remember { mutableStateOf(StudioState.IDLE) }
    var countdownValue by remember { mutableStateOf(3) }
    var collectedBatch by remember { mutableStateOf<MutableList<FloatArray>>(mutableListOf()) }
    var statusMessage by remember { mutableStateOf("") }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Filtered signs list
    val currentSignList = remember(selectedTab, selectedCategory, searchQuery) {
        val baseList = if (selectedTab == 0) IslVocabulary.LETTERS else IslVocabulary.WORDS_300
        baseList.filter { sign ->
            val matchCat = (selectedCategory == "All") || (sign.category == selectedCategory)
            val matchQuery = searchQuery.isBlank() || sign.name.contains(searchQuery.trim(), ignoreCase = true)
            matchCat && matchQuery
        }
    }

    val currentSign: SignInfo? = currentSignList.getOrNull(currentSignIndex) ?: currentSignList.firstOrNull()

    // Active anchor detection & matching across all detected hands
    val activeAnchorZone = remember(hands, pose) {
        if (hands.isNotEmpty() && pose != null) {
            var matched = "OPEN_SPACE"
            for (hand in hands) {
                val (zone, _) = FeatureExtractor.detectBodyAnchor(hand, pose)
                if (zone != "OPEN_SPACE") {
                    matched = zone
                    if (currentSign?.targetAnchor != null && zone == currentSign.targetAnchor) {
                        break
                    }
                }
            }
            matched
        } else "OPEN_SPACE"
    }

    val isAnchorMatched = remember(activeAnchorZone, currentSign) {
        currentSign?.targetAnchor != null && activeAnchorZone == currentSign.targetAnchor
    }

    // Target reticle position
    val targetAnchorPt: Point3D? = remember(pose, currentSign) {
        val anchorName = currentSign?.targetAnchor ?: return@remember null
        if (pose == null || pose.points.size < 25) return@remember null
        val p = pose.points
        val pNose = p[0]
        val pMl = p[9]
        val pMr = p[10]
        val pLsh = p[11]
        val pRsh = p[12]
        val pLwrist = p[15]
        val pRwrist = p[16]
        val shoulderDist = kotlin.math.sqrt(
            (pLsh.x - pRsh.x) * (pLsh.x - pRsh.x) + (pLsh.y - pRsh.y) * (pLsh.y - pRsh.y)
        )
        val scale = if (shoulderDist > 0.15f) shoulderDist else 0.15f
        val mouthCenter = Point3D((pMl.x + pMr.x) / 2f, (pMl.y + pMr.y) / 2f, (pMl.z + pMr.z) / 2f)

        when (anchorName) {
            "FOREHEAD" -> Point3D(pNose.x, pNose.y - 0.22f * scale, pNose.z)
            "CHIN" -> Point3D(mouthCenter.x, mouthCenter.y + 0.12f * scale, mouthCenter.z)
            "MOUTH_LIPS" -> mouthCenter
            "CHEST" -> Point3D((pLsh.x + pRsh.x) / 2f, (pLsh.y + pRsh.y) / 2f, (pLsh.z + pRsh.z) / 2f)
            "WRIST" -> pLwrist
            "SHOULDER" -> pRsh
            "WAIST" -> Point3D((p[23].x + p[24].x) / 2f, (p[23].y + p[24].y) / 2f, (p[23].z + p[24].z) / 2f)
            else -> null
        }
    }

    // Recording collection logic (fast capture with online feature augmentation)
    LaunchedEffect(studioState, hands, pose) {
        if (studioState == StudioState.RECORDING && currentSign != null) {
            val expectedHands = currentSign.expectedHands
            if (hands.size >= expectedHands) {
                val feats = FeatureExtractor.extractSpatialFeatures(hands, pose)
                collectedBatch.add(feats)

                // Add 1 slight jittered exemplar to double sample throughput without arm fatigue
                val jittered = FloatArray(feats.size) { i ->
                    val v = feats[i]
                    if (i < 164) v + (kotlin.random.Random.nextFloat() - 0.5f) * 0.008f else v
                }
                collectedBatch.add(jittered)

                if (collectedBatch.size >= 300) {
                    val total = datasetManager.saveSamples(currentSign.name, collectedBatch)
                    recordedCounts = datasetManager.getRecordedCounts()
                    statusMessage = "🎉 Saved 300 samples for '${currentSign.name}'! Total: $total"
                    studioState = StudioState.SUCCESS
                    delay(2500)
                    studioState = StudioState.IDLE
                    collectedBatch.clear()
                }
            }
        }
    }

    // Countdown animation
    LaunchedEffect(studioState) {
        if (studioState == StudioState.COUNTDOWN) {
            countdownValue = 3
            while (countdownValue > 0) {
                delay(1000)
                countdownValue -= 1
            }
            collectedBatch.clear()
            studioState = StudioState.RECORDING
        }
    }

    val totalReady = remember(recordedCounts) {
        recordedCounts.values.count { it >= 300 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ISL Training Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "220D Hand + Body Articulation • Ready: $totalReady / ${IslVocabulary.ALL_SIGNS.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Export Dataset Button
                    FilledTonalButton(
                        onClick = {
                            val file = datasetManager.exportCompleteDataset()
                            if (file != null) {
                                exportedFile = file
                                showExportDialog = true
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Export Dataset", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export File", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(onClick = onToggleCamera) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch Camera")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mode Selector Tabs (Letters vs 300 Words)
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        currentSignIndex = 0
                        selectedCategory = "All"
                    },
                    text = { Text("Letters (${IslVocabulary.LETTERS.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        currentSignIndex = 0
                        selectedCategory = "All"
                    },
                    text = { Text("300 Static Words", fontWeight = FontWeight.SemiBold) }
                )
            }

            // Word Category Filter Chips
            if (selectedTab == 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(IslVocabulary.CATEGORIES) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                currentSignIndex = 0
                            },
                            label = { Text(cat, fontSize = 12.sp) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            // Horizontal Sign Carousel Ribbon
            val carouselState = rememberLazyListState()
            LazyRow(
                state = carouselState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentSignList.indices.toList()) { idx ->
                    val signItem = currentSignList[idx]
                    val count = recordedCounts[signItem.name] ?: 0
                    val isSelected = currentSignIndex == idx

                    val chipBg = when {
                        count >= 300 -> Color(0xFF10B981) // Emerald Green
                        count > 0 -> Color(0xFFF59E0B) // Amber
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val textColor = when {
                        count > 0 -> Color(0xFF0F172A)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(chipBg)
                            .then(
                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                                else Modifier
                            )
                            .clickable { currentSignIndex = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${signItem.name}: $count",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = textColor
                        )
                    }
                }
            }

            // Main Camera Viewport + Cybernetic Tracking Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp)
            ) {
                // Camera Preview
                AndroidView(
                    factory = { _ ->
                        (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Hand + Body Pose Cybernetic Overlay
                SpatialOverlay(
                    hands = hands,
                    pose = pose,
                    targetAnchor = currentSign?.targetAnchor,
                    targetAnchorPt = targetAnchorPt,
                    isAnchorMatched = isAnchorMatched,
                    cameraResolution = cameraResolution,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Status Pill
                if (currentSign != null) {
                    val count = recordedCounts[currentSign.name] ?: 0
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.88f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = currentSign.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("${currentSign.expectedHands} HANDS", fontSize = 11.sp) },
                                shape = CircleShape
                            )
                            if (currentSign.targetAnchor != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = if (isAnchorMatched) "✓ [${currentSign.targetAnchor}]" else "[${currentSign.targetAnchor}]",
                                            fontSize = 11.sp,
                                            color = if (isAnchorMatched) Color(0xFF34D399) else Color(0xFFFBBF24)
                                        )
                                    },
                                    shape = CircleShape
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Samples: $count / 300",
                                fontSize = 12.sp,
                                color = if (count >= 300) Color(0xFF34D399) else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                // Countdown Overlay
                if (studioState == StudioState.COUNTDOWN) {
                    Card(
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.94f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFF59E0B))),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "$countdownValue",
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFF59E0B)
                            )
                            Text(
                                text = "HOLD POSTURE FOR '${currentSign?.name}'",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // Recording Indicator
                if (studioState == StudioState.RECORDING) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.92f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BURST 220D: ${collectedBatch.size} / 300 SAMPLES",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Success Message Banner
                if (statusMessage.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.94f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = statusMessage,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Bottom Posture Guide Card & Record Trigger FAB
            if (currentSign != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSign.guidePosture,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentSign.guideDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Large Expressive Record Button
                            Button(
                                onClick = {
                                    if (studioState == StudioState.IDLE) {
                                        studioState = StudioState.COUNTDOWN
                                    }
                                },
                                enabled = studioState == StudioState.IDLE,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (recordedCounts[currentSign.name] ?: 0 >= 300) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if ((recordedCounts[currentSign.name] ?: 0) >= 300) "Re-Record" else "Record 300",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Export Confirmation Dialog
    if (showExportDialog && exportedFile != null) {
        val totalSamples = recordedCounts.values.sum()
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp)) },
            title = { Text("Training Dataset Exported", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Successfully compiled your recorded ISL dataset:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Total Classes: $totalReady signs", fontWeight = FontWeight.Medium)
                    Text("• Total 220D Samples: $totalSamples vectors", fontWeight = FontWeight.Medium)
                    Text("• Format: JSON (220D Hand + Body Articulation)", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Saved to your phone's Downloads folder as 'signora_isl_dataset.json'. You can also share it directly now to send to PC.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        exportedFile?.let { datasetManager.shareDatasetFile(it) }
                        showExportDialog = false
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Dataset Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
