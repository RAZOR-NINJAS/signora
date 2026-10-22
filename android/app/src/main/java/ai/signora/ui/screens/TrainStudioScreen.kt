package ai.signora.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import ai.signora.audio.SignAudio
import ai.signora.engine.*
import ai.signora.ml.BodySegmentation
import ai.signora.ml.FeatureExtractor
import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import ai.signora.models.PoseLandmarks
import ai.signora.models.SignInfo
import ai.signora.training.IslVocabulary
import ai.signora.training.OnDeviceIslTrainer
import ai.signora.training.TrainingDatasetManager
import ai.signora.ui.components.SpatialOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StudioState {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED,
    SAVING,
    SUCCESS
}

enum class SignMode {
    STATIC,
    MOTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainStudioScreen(
    previewView: PreviewView,
    hands: List<HandLandmarks>,
    pose: PoseLandmarks?,
    onToggleCamera: () -> Unit,
    cameraResolution: Pair<Int, Int> = Pair(480, 640),
    onReloadModels: () -> Unit = {},
    bottomBarPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onAttachPreview: (PreviewView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val datasetManager = remember { TrainingDatasetManager(context) }

    val motionTracker = remember { HandMotionTracker(maxHistory = 45) }
    var signMode by remember { mutableStateOf(SignMode.STATIC) }

    // Target sample collection count: 1,000 per recording batch, 10,000 max cap per sign
    val batchTargetSamples = 1000
    val maxCapSamples = 10000

    // Vocabulary & Selection
    var selectedTab by remember { mutableStateOf(0) } // 0: Letters, 1: 300 Words
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var currentSignIndex by remember { mutableStateOf(0) }

    // Recorded counts cache
    var recordedCounts by remember { mutableStateOf(datasetManager.getRecordedCounts()) }

    LaunchedEffect(Unit) {
        val counts = withContext(Dispatchers.IO) {
            datasetManager.getRecordedCounts()
        }
        recordedCounts = counts
    }

    // Studio state
    var studioState by remember { mutableStateOf(StudioState.IDLE) }
    var countdownValue by remember { mutableStateOf(3) }
    var collectedBatch by remember { mutableStateOf<MutableList<FloatArray>>(mutableListOf()) }
    var recordedSamplesCount by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("") }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val handleExport: () -> Unit = {
        val totalSamples = recordedCounts.values.sum()
        if (totalSamples == 0) {
            Toast.makeText(context, "No samples recorded yet. Record at least one sign before exporting.", Toast.LENGTH_LONG).show()
        } else {
            isExporting = true
            coroutineScope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        datasetManager.exportCompleteDataset()
                    }
                    if (file != null && file.exists()) {
                        exportedFile = file
                        showExportDialog = true
                    } else {
                        Toast.makeText(context, "Failed to compile dataset. Please check device storage.", Toast.LENGTH_LONG).show()
                    }
                } catch (t: Throwable) {
                    Toast.makeText(context, "Export error: ${t.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // Retrain state
    var showRetrainDialog by remember { mutableStateOf(false) }
    var isTrainingRunning by remember { mutableStateOf(false) }
    var trainingProgressValue by remember { mutableStateOf(0f) }
    var trainingStatusText by remember { mutableStateOf("") }

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

    // Active anatomical body segments (supports simultaneous dual-hand multi-zone detection)
    val activeSegments = remember(hands, pose) {
        BodySegmentation.detectActiveSegments(hands, pose)
    }
    val activeSegment = activeSegments.firstOrNull()

    // Active anchor detection & matching across all detected hands
    val activeAnchorZone = remember(hands, pose, activeSegments) {
        if (activeSegments.size >= 2) {
            "${activeSegments[0].shortTag} + ${activeSegments[1].shortTag}"
        } else if (activeSegments.size == 1) {
            activeSegments[0].id
        } else if (hands.isNotEmpty() && pose != null) {
            var matched = "OPEN_SPACE"
            val zones = mutableListOf<String>()
            for (hand in hands) {
                val (zone, _) = FeatureExtractor.detectBodyAnchor(hand, pose)
                if (zone != "OPEN_SPACE" && !zones.contains(zone)) {
                    zones.add(zone)
                    if (currentSign?.targetAnchor != null && zone == currentSign.targetAnchor) {
                        matched = zone
                        break
                    }
                }
            }
            if (matched != "OPEN_SPACE") matched
            else if (zones.size >= 2) "${zones[0]} + ${zones[1]}"
            else zones.firstOrNull() ?: "OPEN_SPACE"
        } else "OPEN_SPACE"
    }

    val isAnchorMatched = remember(activeAnchorZone, activeSegments, currentSign) {
        val target = currentSign?.targetAnchor ?: return@remember false
        if (activeSegments.any { it.matches(target) }) return@remember true
        if (activeAnchorZone == target || activeAnchorZone.contains(target)) return@remember true
        when (target) {
            "CHEST" -> activeSegments.any { it.id in listOf("CHEST", "CHEST_CENTER", "CHEST_LEFT", "CHEST_RIGHT") } || activeAnchorZone in listOf("CHEST", "CHEST_CENTER", "CHEST_LEFT", "CHEST_RIGHT")
            "CHEST_CENTER" -> activeSegments.any { it.id in listOf("CHEST", "CHEST_CENTER") } || activeAnchorZone in listOf("CHEST", "CHEST_CENTER")
            "CHEST_LEFT" -> activeSegments.any { it.id in listOf("CHEST_LEFT", "HEART") } || activeAnchorZone in listOf("CHEST_LEFT", "HEART")
            "MID_TORSO" -> activeSegments.any { it.id in listOf("MID_TORSO", "STOMACH") } || activeAnchorZone in listOf("MID_TORSO", "STOMACH")
            "THROAT" -> activeSegments.any { it.id in listOf("THROAT", "NECK") } || activeAnchorZone in listOf("THROAT", "NECK")
            "FOREHEAD" -> activeSegments.any { it.id in listOf("FOREHEAD", "CROWN") } || activeAnchorZone in listOf("FOREHEAD", "CROWN")
            else -> false
        }
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
        val pCrown = Point3D(pNose.x, pNose.y - 0.40f * scale, pNose.z)
        val forehead = Point3D(pNose.x, pNose.y - 0.22f * scale, pNose.z)
        val chin = Point3D(mouthCenter.x, mouthCenter.y + 0.12f * scale, mouthCenter.z)
        val throat = Point3D(mouthCenter.x, mouthCenter.y + 0.30f * scale, mouthCenter.z)
        val chestCenter = Point3D((pLsh.x + pRsh.x) / 2f, (pLsh.y + pRsh.y) / 2f, (pLsh.z + pRsh.z) / 2f)
        val chestLeft = Point3D(pLsh.x * 0.38f + chestCenter.x * 0.62f, chestCenter.y, chestCenter.z)
        val chestRight = Point3D(pRsh.x * 0.38f + chestCenter.x * 0.62f, chestCenter.y, chestCenter.z)
        val waistCenter = Point3D((p[23].x + p[24].x) / 2f, (p[23].y + p[24].y) / 2f, (p[23].z + p[24].z) / 2f)
        val midTorso = Point3D(chestCenter.x, (chestCenter.y + waistCenter.y) / 2f, chestCenter.z)

        when (anchorName) {
            "CROWN" -> pCrown
            "FOREHEAD" -> forehead
            "TEMPLE" -> Point3D(pRsh.x * 0.4f + pNose.x * 0.6f, forehead.y, pNose.z)
            "EYES" -> Point3D(pNose.x, pNose.y - 0.08f * scale, pNose.z)
            "NOSE" -> pNose
            "MOUTH_LIPS" -> mouthCenter
            "CHIN" -> chin
            "THROAT" -> throat
            "CHEST", "CHEST_CENTER" -> chestCenter
            "CHEST_LEFT", "HEART" -> chestLeft
            "CHEST_RIGHT" -> chestRight
            "MID_TORSO", "STOMACH" -> midTorso
            "WAIST" -> waistCenter
            "WRIST" -> pLwrist
            "SHOULDER" -> pRsh
            else -> null
        }
    }

    // Update motion tracker continuously on landmark feed
    var motionState by remember { mutableStateOf(MotionState()) }
    var rightTrail by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var leftTrail by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

    LaunchedEffect(hands) {
        val rHand = hands.firstOrNull { it.isRightHand } ?: hands.firstOrNull()
        val lHand = if (hands.size > 1) hands.firstOrNull { !it.isRightHand && it != rHand } ?: hands.getOrNull(1) else null
        val rWrist = rHand?.points?.firstOrNull()?.let { Pair(it.x, it.y) }
        val lWrist = if (lHand != null && lHand != rHand) lHand.points.firstOrNull()?.let { Pair(it.x, it.y) } else null

        motionState = motionTracker.update(rWrist, lWrist)
        rightTrail = motionTracker.getTrailPoints(isRight = true)
        leftTrail = motionTracker.getTrailPoints(isRight = false)
    }

    // Recording collection logic (fast capture with online feature augmentation)
    LaunchedEffect(studioState, hands, pose) {
        if (studioState == StudioState.RECORDING && currentSign != null) {
            val minExpectedHands = if (!currentSign.isWord || selectedTab == 0 || currentSign.name == "MY_NAME_IS") 1 else currentSign.expectedHands
            if (hands.size >= minExpectedHands) {
                // If in MOTION mode, ensure user is actively gesturing
                val isMotionMode = signMode == SignMode.MOTION
                val canRecord = if (isMotionMode) motionState.isMoving else true

                if (canRecord) {
                    val feats = FeatureExtractor.extractSpatialFeatures(hands, pose)
                    collectedBatch.add(feats)

                    // Add 1 slight jittered exemplar to double sample throughput without arm fatigue
                    val jittered = FloatArray(feats.size) { i ->
                        val v = feats[i]
                        if (i < 164) v + (kotlin.random.Random.nextFloat() - 0.5f) * 0.008f else v
                    }
                    collectedBatch.add(jittered)
                    recordedSamplesCount = collectedBatch.size

                    val activeKey = if (isMotionMode) "${currentSign.name}_MOTION" else currentSign.name
                    val currentCount = recordedCounts[activeKey] ?: 0

                    if (collectedBatch.size >= batchTargetSamples || (currentCount + collectedBatch.size) >= maxCapSamples) {
                        studioState = StudioState.SAVING
                        val maxAllowed = (maxCapSamples - currentCount).coerceAtLeast(0)
                        val samplesToSave = if (collectedBatch.size > maxAllowed) collectedBatch.take(maxAllowed) else collectedBatch.toList()
                        val signName = currentSign.name
                        coroutineScope.launch {
                            try {
                                statusMessage = "Saving samples to storage..."
                                val total = withContext(Dispatchers.IO) {
                                    datasetManager.saveSamples(signName, samplesToSave, isMotion = isMotionMode)
                                }
                                val counts = withContext(Dispatchers.IO) {
                                    datasetManager.getRecordedCounts()
                                }
                                recordedCounts = counts
                                val modeLabel = if (isMotionMode) "MOTION" else "STATIC"
                                statusMessage = "🎉 Saved ${samplesToSave.size} $modeLabel samples for '$signName'! Total: $total / 10k"
                                studioState = StudioState.SUCCESS
                                delay(2000)
                                statusMessage = ""
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                statusMessage = "Error saving samples: ${e.message}"
                                delay(2000)
                            } finally {
                                collectedBatch.clear()
                                recordedSamplesCount = 0
                                studioState = StudioState.IDLE
                            }
                        }
                    }
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
            recordedSamplesCount = 0
            studioState = StudioState.RECORDING
        }
    }

    val totalReady = remember(recordedCounts) {
        recordedCounts.count { it.value >= 10 }
    }

    // Ensure Studio uses full-sensor FIT_CENTER without cropping
    DisposableEffect(previewView) {
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        onDispose {
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ISL Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (totalReady >= 2) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { showRetrainDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                if (totalReady >= 2) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = "$totalReady/${IslVocabulary.ALL_SIGNS.size} Ready",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (totalReady >= 2) Color(0xFF10B981) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleCamera) {
                        Icon(
                            Icons.Filled.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomBarPadding)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ─── Streamlined Sign Selector Strip ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Segmented Pill Toggle: Letters vs 300 Words
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    val tabLettersSelected = selectedTab == 0
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (tabLettersSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.clickable {
                            selectedTab = 0
                            currentSignIndex = 0
                            selectedCategory = "All"
                        }
                    ) {
                        Text(
                            text = "Letters (27)",
                            fontSize = 12.sp,
                            fontWeight = if (tabLettersSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (tabLettersSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    val tabWordsSelected = selectedTab == 1
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (tabWordsSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.clickable {
                            selectedTab = 1
                            currentSignIndex = 0
                            selectedCategory = "All"
                        }
                    ) {
                        Text(
                            text = "300 Words",
                            fontSize = 12.sp,
                            fontWeight = if (tabWordsSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (tabWordsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Category Dropdown Filter (Words mode)
                if (selectedTab == 1) {
                    Box {
                        AssistChip(
                            onClick = { categoryMenuExpanded = true },
                            label = {
                                Text(
                                    text = if (selectedCategory == "All") "📁 All ▾" else "📁 $selectedCategory ▾",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            IslVocabulary.CATEGORIES.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cat,
                                            fontWeight = if (cat == selectedCategory) FontWeight.Bold else FontWeight.Normal,
                                            color = if (cat == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedCategory = cat
                                        currentSignIndex = 0
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Mode Selector Strip: Static (2,000) vs Motion (400)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    val isStatic = signMode == SignMode.STATIC
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isStatic) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.clickable {
                            if (studioState == StudioState.IDLE) {
                                signMode = SignMode.STATIC
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Filled.PanTool,
                                contentDescription = null,
                                tint = if (isStatic) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Static",
                                fontSize = 11.sp,
                                fontWeight = if (isStatic) FontWeight.Bold else FontWeight.Medium,
                                color = if (isStatic) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isMotion = signMode == SignMode.MOTION
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMotion) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                        modifier = Modifier.clickable {
                            if (studioState == StudioState.IDLE) {
                                signMode = SignMode.MOTION
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Filled.Speed,
                                contentDescription = null,
                                tint = if (isMotion) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Motion",
                                fontSize = 11.sp,
                                fontWeight = if (isMotion) FontWeight.Bold else FontWeight.Medium,
                                color = if (isMotion) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (signMode == SignMode.MOTION) {
                    val expMotion = currentSign?.expectedMotion ?: "DYNAMIC"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "PATTERN: $expMotion",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Horizontal Sign Carousel Ribbon (Compact)
            val carouselState = rememberLazyListState()
            LaunchedEffect(currentSignIndex) {
                if (currentSignList.isNotEmpty() && currentSignIndex in currentSignList.indices) {
                    carouselState.animateScrollToItem(currentSignIndex)
                }
            }
            LazyRow(
                state = carouselState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(currentSignList.indices.toList()) { idx ->
                    val signItem = currentSignList[idx]
                    val signKey = if (signMode == SignMode.MOTION) "${signItem.name}_MOTION" else signItem.name
                    val count = recordedCounts[signKey] ?: 0
                    val isSelected = currentSignIndex == idx
                    val isCapped = count >= maxCapSamples

                    val chipBg = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isCapped -> Color(0xFF166534)
                        count >= batchTargetSamples -> MaterialTheme.colorScheme.tertiaryContainer
                        count > 0 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val textColor = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isCapped -> Color(0xFF86EFAC)
                        count >= batchTargetSamples -> MaterialTheme.colorScheme.onTertiaryContainer
                        count > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = chipBg,
                        modifier = Modifier.clickable { currentSignIndex = idx }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = signItem.name,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = textColor
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCapped) "✓" else if (count >= 1000) "${count / 1000}k" else "$count",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Native 4:3 Sensor Viewport (3:4 portrait aspect ratio) ───────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                ) {
                    // Full Sensor 4:3 Viewfinder
                    AndroidView(
                        factory = { _ ->
                            (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                            previewView.apply {
                                scaleType = PreviewView.ScaleType.FIT_CENTER
                            }.also {
                                onAttachPreview(it)
                            }
                        },
                        update = { view ->
                            view.scaleType = PreviewView.ScaleType.FIT_CENTER
                            onAttachPreview(view)
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
                        isFitCenter = true,
                        rightMotionTrail = rightTrail,
                        leftMotionTrail = leftTrail,
                        modifier = Modifier.fillMaxSize()
                    )

                // Minimal Floating HUD in top corners of viewfinder
                if (currentSign != null) {
                    val activeKey = if (signMode == SignMode.MOTION) "${currentSign.name}_MOTION" else currentSign.name
                    val count = recordedCounts[activeKey] ?: 0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Pill: Sign Name + Expected Hands + Mode Badge + Target Anchor Match
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = currentSign.name,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${currentSign.expectedHands}H",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (signMode == SignMode.MOTION) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = if (signMode == SignMode.MOTION) "MOTION" else "STATIC",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (signMode == SignMode.MOTION) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (currentSign.targetAnchor != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isAnchorMatched) "✓ [${currentSign.targetAnchor}]" else "[${currentSign.targetAnchor}]",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAnchorMatched) Color(0xFF4ADE80) else Color(0xFFFACC15)
                                    )
                                }
                                if (SignAudio.hasCue(currentSign.name)) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { SignAudio.play(context, currentSign.name) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Play sign audio",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Right Pill: Samples Count out of 10,000 limit
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (count >= maxCapSamples) Color(0xFF166534).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = if (count >= maxCapSamples) "10,000 / 10k (CAP)" else "$count / 10,000",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (count >= maxCapSamples) Color(0xFF86EFAC) else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Motion dynamic banner during motion mode recording
                if (signMode == SignMode.MOTION && studioState == StudioState.RECORDING) {
                    val activePattern = if (motionState.dualMotion != DUAL_STATIC) motionState.dualMotion else motionState.pattern
                    val motionTag = if (motionState.isMoving) "⚡ MOVING ($activePattern)" else "WAITING FOR MOTION..."
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (motionState.isMoving) Color(0xFF06B6D4).copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.70f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    ) {
                        Text(
                            text = motionTag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Countdown Overlay (Frosted Center Dial)
                if (studioState == StudioState.COUNTDOWN) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.80f),
                        modifier = Modifier
                            .size(110.dp)
                            .align(Alignment.Center)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$countdownValue",
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                // Recording Indicator (Slim top strip, never obstructs hands; tap to pause)
                if (studioState == StudioState.RECORDING) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.90f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 46.dp)
                            .clickable { studioState = StudioState.PAUSED }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Filled.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REC: $recordedSamplesCount / $batchTargetSamples (Tap to Pause)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (studioState == StudioState.PAUSED) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFD97706).copy(alpha = 0.95f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 46.dp)
                            .clickable { studioState = StudioState.RECORDING }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PAUSED: $recordedSamplesCount / $batchTargetSamples (Tap to Resume)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (studioState == StudioState.SAVING) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SAVING SAMPLES (Cap: 10,000)...",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Compact Non-intrusive Toast at bottom of viewfinder
                if (statusMessage.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.90f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = statusMessage,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ─── Ergonomic Bottom Control Deck ─────────────────────────────────────
            if (currentSign != null) {
                val activeKey = if (signMode == SignMode.MOTION) "${currentSign.name}_MOTION" else currentSign.name
                val count = recordedCounts[activeKey] ?: 0

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Posture guide hint in a single clean line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (signMode == SignMode.MOTION && currentSign.motionDescription.isNotBlank())
                                    "${currentSign.guidePosture} • Motion: ${currentSign.motionDescription}"
                                else
                                    currentSign.guidePosture,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }

                        // ─── Primary Capture Action (Tier 1) ───────────────────────
                        when (studioState) {
                            StudioState.RECORDING -> {
                                Button(
                                    onClick = { studioState = StudioState.PAUSED },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Pause,
                                        contentDescription = "Pause",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PAUSE RECORDING ($recordedSamplesCount / $batchTargetSamples)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                            StudioState.PAUSED -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedIconButton(
                                        onClick = {
                                            collectedBatch.clear()
                                            recordedSamplesCount = 0
                                            studioState = StudioState.IDLE
                                            statusMessage = "Batch discarded"
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Discard Batch",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Button(
                                        onClick = { studioState = StudioState.RECORDING },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Resume",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "RESUME ($recordedSamplesCount / $batchTargetSamples)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }

                                    if (recordedSamplesCount >= 30) {
                                        Button(
                                            onClick = {
                                                studioState = StudioState.SAVING
                                                val samplesToSave = collectedBatch.toList()
                                                val signName = currentSign.name
                                                val isMotionMode = signMode == SignMode.MOTION
                                                coroutineScope.launch {
                                                    try {
                                                        statusMessage = "Saving samples..."
                                                        val total = withContext(Dispatchers.IO) {
                                                            datasetManager.saveSamples(signName, samplesToSave, isMotion = isMotionMode)
                                                        }
                                                        recordedCounts = withContext(Dispatchers.IO) { datasetManager.getRecordedCounts() }
                                                        val modeLabel = if (isMotionMode) "MOTION" else "STATIC"
                                                        statusMessage = "🎉 Saved ${samplesToSave.size} $modeLabel samples for '$signName'! Total: $total / 10k"
                                                        studioState = StudioState.SUCCESS
                                                        delay(2000)
                                                        statusMessage = ""
                                                    } catch (e: Exception) {
                                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                                        statusMessage = "Error: ${e.message}"
                                                        delay(2000)
                                                    } finally {
                                                        collectedBatch.clear()
                                                        recordedSamplesCount = 0
                                                        studioState = StudioState.IDLE
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.height(50.dp)
                                        ) {
                                            Icon(Icons.Filled.Save, contentDescription = "Save Early", modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save ($recordedSamplesCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            else -> {
                                val isCapped = count >= maxCapSamples
                                Button(
                                    onClick = {
                                        if (studioState == StudioState.IDLE && !isCapped) {
                                            studioState = StudioState.COUNTDOWN
                                        }
                                    },
                                    enabled = studioState == StudioState.IDLE && !isCapped,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isCapped -> Color(0xFF166534)
                                            count > 0 -> MaterialTheme.colorScheme.primary
                                            else -> Color(0xFFDC2626)
                                        },
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(
                                        if (isCapped) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonChecked,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val recLabel = when {
                                        isCapped -> "Sample Cap Reached (10,000 / 10k)"
                                        count == 0 -> "Record 1,000 Samples (Cap: 10k)"
                                        else -> "+ Record 1,000 More ($count / 10k)"
                                    }
                                    Text(
                                        text = recLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // ─── Utility Actions: Clear / Train Model / Export Dataset (Tier 2) ───
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (count > 0) {
                                OutlinedIconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                datasetManager.clearSignSamples(currentSign.name, isMotion = signMode == SignMode.MOTION)
                                            }
                                            recordedCounts = datasetManager.getRecordedCounts()
                                            val modeLbl = if (signMode == SignMode.MOTION) "motion" else "static"
                                            statusMessage = "Cleared $modeLbl samples for '${currentSign.name}'"
                                        }
                                    },
                                    enabled = studioState == StudioState.IDLE,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = "Clear Samples",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = { showRetrainDialog = true },
                                enabled = studioState == StudioState.IDLE,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (totalReady >= 2) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (totalReady >= 2) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Bolt,
                                    contentDescription = "Train Model",
                                    tint = if (totalReady >= 2) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (totalReady >= 2) "Train ($totalReady)" else "Train",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = { handleExport() },
                                enabled = studioState == StudioState.IDLE && !isExporting,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = "Export Dataset",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Export",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
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
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp)) },

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        showExportDialog = false
                        showClearAllDialog = true
                    }) {
                        Text("Delete Phone Samples", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Delete All Recorded Samples Confirmation Dialog
    if (showClearAllDialog) {
        val totalSamples = recordedCounts.values.sum()
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Delete All Recorded Samples?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently remove all $totalSamples previously recorded sign and motion samples from this phone's storage. Exported dataset files in Downloads will remain intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                datasetManager.clearAllSamples()
                            }
                            recordedCounts = datasetManager.getRecordedCounts()
                            statusMessage = "All previous samples cleared."
                            Toast.makeText(context, "All previous samples removed", Toast.LENGTH_SHORT).show()
                            showClearAllDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Delete All Samples")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // On-Device Neural Retraining Dialog
    if (showRetrainDialog) {
        val allCounts = recordedCounts
        val qualifyingSigns = allCounts.filter { it.value >= 10 }
        val totalSamples = allCounts.values.sum()

        AlertDialog(
            onDismissRequest = {
                if (!isTrainingRunning) showRetrainDialog = false
            },
            icon = {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (isTrainingRunning) "Training ISL Neural Engine..." else "On-Device ISL Retraining",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isTrainingRunning) {
                        Text(
                            text = trainingStatusText.ifBlank { "Compiling dataset and initializing weights..." },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { trainingProgressValue },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(trainingProgressValue * 100).toInt()}% Complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Train a custom 220D multi-class neural network on your device using all recorded ISL posture samples.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("• Qualifying Classes (>= 10 samples): ${qualifyingSigns.size}", fontWeight = FontWeight.SemiBold)
                                Text("• Total Recorded Samples: $totalSamples vectors", fontWeight = FontWeight.SemiBold)
                                Text("• Target Architecture: 220D -> 128 (LN+GELU) -> 64 -> ${qualifyingSigns.size} Classes", fontSize = 12.sp)
                                Text("• Speed: ~2-4 seconds on device CPU", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (qualifyingSigns.size < 2) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "⚠️ Need at least 2 distinct signs with >= 10 samples to train classification.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (trainingStatusText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = trainingStatusText,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                    }
                }
            },
            confirmButton = {
                if (!isTrainingRunning) {
                    Button(
                        onClick = {
                            if (qualifyingSigns.size >= 2) {
                                isTrainingRunning = true
                                trainingProgressValue = 0f
                                trainingStatusText = "Loading dataset..."
                                coroutineScope.launch {
                                    val dataset = withContext(Dispatchers.IO) {
                                        datasetManager.loadAllSamples()
                                    }
                                    val trainer = OnDeviceIslTrainer(context)
                                    val summary = trainer.train(
                                        dataset = dataset,
                                        epochs = 40,
                                        batchSize = 64
                                    ) { progress ->
                                        trainingProgressValue = progress.epoch.toFloat() / progress.totalEpochs
                                        trainingStatusText = progress.message
                                    }

                                    isTrainingRunning = false
                                    if (summary.success) {
                                        trainingStatusText = "✓ Success! Trained ${summary.totalClasses} classes (${summary.totalSamples} samples) with ${(summary.finalAccuracy * 100).toInt()}% acc in ${summary.durationMs}ms."
                                        statusMessage = "⚡ New ISL Neural Engine trained & activated!"
                                        onReloadModels()
                                    } else {
                                        trainingStatusText = "❌ Training failed: ${summary.errorMessage}"
                                    }
                                }
                            }
                        },
                        enabled = qualifyingSigns.size >= 2,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Train Now")
                    }
                }
            },
            dismissButton = {
                if (!isTrainingRunning) {
                    TextButton(onClick = { showRetrainDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
