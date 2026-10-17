package ai.signora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ai.signora.camera.CameraXManager
import ai.signora.engine.DebounceEngine
import ai.signora.ml.BodySegmentation
import ai.signora.ml.FeatureExtractor
import ai.signora.ml.SignoraMLP
import ai.signora.models.*
import ai.signora.nlp.GlossToken
import ai.signora.nlp.IslGrammarSynthesizer
import ai.signora.nlp.SentenceBuffer
import ai.signora.recovery.AutoAssetManager
import ai.signora.tts.SpeechManager
import ai.signora.profiler.TrackingProfiler
import ai.signora.ui.SignoraScreen
import ai.signora.ui.theme.AppThemeMode
import ai.signora.ui.theme.AppPalette
import ai.signora.ui.theme.SignoraTheme
import ai.signora.vision.HandLandmarkerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var assetManager: AutoAssetManager
    private lateinit var speechManager: SpeechManager
    private var cameraManager: CameraXManager? = null
    private var handHelper: HandLandmarkerHelper? = null
    private var poseHelper: ai.signora.vision.PoseLandmarkerHelper? = null

    private var aslMlp: SignoraMLP? = null
    private var islMlp: SignoraMLP? = null

    private val debounceEngine = DebounceEngine(threshold = 0.30f, minMargin = 0.02f, voteWindowSize = 2)

    // ── NLP Pipeline ─────────────────────────────────────────────────────────
    private var nlpState by mutableStateOf(NlpState())
    private val sentenceBuffer = SentenceBuffer(sentencePauseMs = 5000L) { completedTokens ->
        val sentence = IslGrammarSynthesizer.synthesize(completedTokens)
        if (sentence.isNotBlank()) {
            val glossLabels = completedTokens.map { it.label }
            nlpState = nlpState.copy(
                synthesizedSentence = sentence,
                pendingGlosses = glossLabels
            )
            if (!isMuted) {
                speechManager.speak(sentence)
            }
        }
    }

    private var hasCameraPermission by mutableStateOf(false)
    private var assetStatus by mutableStateOf(AutoAssetManager.AssetStatus(isReady = false, message = "Initializing..."))
    private var currentMode by mutableStateOf(RecognitionMode.ISL)
    private var dwellProfile by mutableStateOf(DwellProfile.STANDARD)
    private var customDwellMs by mutableStateOf(900L)
    private var confidenceThreshold by mutableStateOf(0.30f)
    private var minMargin by mutableStateOf(0.02f)
    private var cameraResolution by mutableStateOf(Pair(480, 640))
    private var isMuted by mutableStateOf(false)
    private var isFrontCamera by mutableStateOf(true)
    private var isTorchOn by mutableStateOf(false)
    private var isPaused by mutableStateOf(false)
    private var autoCollapseEnabled by mutableStateOf(true)
    private var latestFrameId by mutableStateOf(0L)
    private var themeMode by mutableStateOf(AppThemeMode.DARK)
    private var selectedPalette by mutableStateOf(AppPalette.WALLPAPER)
    private var dynamicColorEnabled by mutableStateOf(true)
    private var debounceState by mutableStateOf(DebounceState())
    private var activeLandmarks by mutableStateOf<List<HandLandmarks>>(emptyList())
    private var activePose by mutableStateOf<PoseLandmarks?>(null)

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps by mutableStateOf(30f)

    private lateinit var previewView: PreviewView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            setupVisionPipeline()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        assetManager = AutoAssetManager(this)
        speechManager = SpeechManager(this)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        setContent {
            SignoraTheme(
                themeMode = themeMode,
                selectedPalette = selectedPalette,
                dynamicColor = dynamicColorEnabled
            ) {
                SignoraScreen(
                    previewView = previewView,
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = { checkCameraPermission() },
                    debounceState = debounceState,
                    handLandmarks = activeLandmarks,
                    poseLandmarks = activePose,
                    currentMode = currentMode,
                    dwellProfile = dwellProfile,
                    isMuted = isMuted,
                    isFrontCamera = isFrontCamera,
                    isTorchOn = isTorchOn,
                    isPaused = isPaused,
                    fps = currentFps,
                    isDark = themeMode == AppThemeMode.DARK,
                    themeMode = themeMode,
                    selectedPalette = selectedPalette,
                    dynamicColor = dynamicColorEnabled,
                    autoCollapseEnabled = autoCollapseEnabled,
                    latestFrameId = latestFrameId,
                    customDwellMs = customDwellMs,
                    confidenceThreshold = confidenceThreshold,
                    minMargin = minMargin,
                    cameraResolution = cameraResolution,
                    assetStatus = assetStatus,
                    nlpState = nlpState,
                    onModeChange = { newMode ->
                        currentMode = newMode
                        debounceEngine.clear()
                        sentenceBuffer.clear()
                        nlpState = NlpState()
                        debounceState = debounceState.copy(candidate = "", confidence = 0f, dwellProgress = 0f)
                    },
                    onDwellProfileChange = { newProfile ->
                        dwellProfile = newProfile
                        customDwellMs = newProfile.dwellMs
                        debounceEngine.dwellMs = newProfile.dwellMs
                    },
                    onCustomDwellChange = { ms ->
                        customDwellMs = ms
                        debounceEngine.dwellMs = ms
                    },
                    onConfidenceThresholdChange = { conf ->
                        confidenceThreshold = conf
                        debounceEngine.confidenceThreshold = conf
                    },
                    onMinMarginChange = { margin ->
                        minMargin = margin
                        debounceEngine.minMargin = margin
                    },
                    onAutoCollapseChange = { autoCollapseEnabled = it },
                    onToggleMute = {
                        isMuted = !isMuted
                        speechManager.isMuted = isMuted
                    },
                    onToggleCamera = {
                        cameraManager?.toggleCamera()
                        isFrontCamera = cameraManager?.isFrontCamera ?: true
                        isTorchOn = false
                    },
                    onToggleTorch = {
                        isTorchOn = cameraManager?.toggleTorch() ?: false
                    },
                    onTogglePause = {
                        isPaused = !isPaused
                        cameraManager?.isPaused = isPaused
                    },
                    onToggleTheme = {
                        themeMode = if (themeMode == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
                    },
                    onThemeModeChange = { themeMode = it },
                    onPaletteChange = { selectedPalette = it },
                    onDynamicColorChange = { dynamicColorEnabled = it },
                    onTestVoice = {
                        speechManager.speak("Testing Unspoken Neural Voice synthesizer")
                    },
                    onBackspace = {
                        debounceEngine.backspace()
                        sentenceBuffer.pop()
                        val pending = sentenceBuffer.peek().map { it.label }
                        val livePreview = IslGrammarSynthesizer.synthesize(sentenceBuffer.peek())
                        nlpState = nlpState.copy(
                            pendingGlosses = pending,
                            synthesizedSentence = livePreview
                        )
                        debounceState = debounceState.copy(transcript = debounceEngine.getTranscript())
                    },
                    onClear = {
                        debounceEngine.clear()
                        sentenceBuffer.clear()
                        nlpState = NlpState()
                        debounceState = debounceState.copy(transcript = "")
                    },
                    onSpace = {
                        val state = debounceEngine.update(RecognitionResult("SPACE", 0.99f))
                        debounceState = state
                    },
                    onSpeak = {
                        // Flush pending gloss buffer first, then speak synthesized sentence
                        sentenceBuffer.flush()
                        val toSpeak = nlpState.synthesizedSentence.ifBlank {
                            debounceEngine.getTranscript()
                        }
                        speechManager.speak(toSpeak)
                    },
                    onReloadModels = {
                        lifecycleScope.launch {
                            loadNeuralEngines()
                        }
                    }
                )

            }
        }

        // 1. Immediately verify and prompt for camera permission on startup
        checkCameraPermission()

        // 2. Initialize and self-heal model assets in background
        lifecycleScope.launch {
            val ok = assetManager.ensureModelAssets { status ->
                assetStatus = status
            }
            if (ok) {
                loadNeuralEngines()
                if (hasCameraPermission && cameraManager == null) {
                    setupVisionPipeline()
                }
            }
        }
    }

    private suspend fun loadNeuralEngines() = withContext(Dispatchers.Default) {
        try {
            val aslJson = assetManager.loadModelJsonString("asl_model.json")
            aslMlp = SignoraMLP.fromJson(aslJson)

            val islJson = assetManager.loadModelJsonString("isl_model.json")
            islMlp = SignoraMLP.fromJson(islJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = granted
        if (granted) {
            setupVisionPipeline()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupVisionPipeline() {
        if (cameraManager != null) return

        val landmarkerTaskFile = assetManager.getModelFile("hand_landmarker.task")
        val poseTaskFile = assetManager.getModelFile("pose_landmarker.task")

        handHelper = HandLandmarkerHelper(
            context = this,
            modelFile = landmarkerTaskFile,
            onResults = { hands, frameId, timestamp ->
                onHandsDetected(hands, frameId, timestamp)
            },
            onError = { _ ->
                // Vision pipeline logging
            }
        )

        poseHelper = ai.signora.vision.PoseLandmarkerHelper(
            context = this,
            modelFile = poseTaskFile,
            onResults = { pose, _, _ ->
                activePose = pose
            },
            onError = { _ -> }
        )

        cameraManager = CameraXManager(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            onFrame = { mpImage, rotationDegrees, isFront, frameId, frameTime ->
                handHelper?.detectLiveStream(mpImage, rotationDegrees, isFront, frameId, frameTime)
                poseHelper?.detectLiveStream(mpImage, rotationDegrees, isFront, frameId, frameTime)
            }
        ).apply {
            onResolutionChanged = { w, h ->
                runOnUiThread {
                    cameraResolution = Pair(w, h)
                }
            }
        }
        cameraManager?.startCamera()
    }

    private fun onHandsDetected(hands: List<HandLandmarks>, frameId: Long, timestamp: Long) {
        if (isPaused) return

        activeLandmarks = hands
        latestFrameId = frameId

        // Calculate real-time FPS
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTimestamp >= 1000L) {
            currentFps = frameCount * 1000f / (now - lastFpsTimestamp)
            frameCount = 0
            lastFpsTimestamp = now
        }

        // Tick NLP boundary timer every frame regardless of momentary hand dropout
        sentenceBuffer.tick(now)

        if (hands.isEmpty()) {
            debounceState = debounceEngine.update(null, now).copy(fps = currentFps)
            TrackingProfiler.onVoteDwellDone(frameId)
            TrackingProfiler.onUiStateEmitted(frameId)
            return
        }

        // Extract features based on active mode
        val activeMlp = if (currentMode == RecognitionMode.ASL) aslMlp else islMlp
        if (activeMlp == null) {
            TrackingProfiler.onUiStateEmitted(frameId)
            return
        }

        val feats = if (currentMode == RecognitionMode.ASL) {
            if (hands.size == 1) {
                FeatureExtractor.extractAslFeatures(hands[0].points, hands[0].isRightHand)
            } else {
                // If multiple hands, evaluate both and pick whichever hand produces higher confidence
                val f0 = FeatureExtractor.extractAslFeatures(hands[0].points, hands[0].isRightHand)
                val f1 = FeatureExtractor.extractAslFeatures(hands[1].points, hands[1].isRightHand)
                val r0 = activeMlp.predict(f0)
                val r1 = activeMlp.predict(f1)
                if ((r1?.confidence ?: 0f) > (r0?.confidence ?: 0f)) f1 else f0
            }
        } else {
            if (activeMlp.inDim == 220) {
                FeatureExtractor.extractSpatialFeatures(hands, activePose)
            } else {
                FeatureExtractor.extractIslFeatures(hands)
            }
        }
        TrackingProfiler.onFeaturesExtracted(frameId)

        // Run sub-ms neural forward pass
        val result = activeMlp.predict(feats)
        TrackingProfiler.onMlpDone(frameId)

        // Debounce and dwell tracking
        val newState = debounceEngine.update(result, now).copy(fps = currentFps)
        TrackingProfiler.onVoteDwellDone(frameId)
        debounceState = newState
        TrackingProfiler.onUiStateEmitted(frameId)

        // ── Token push on sign confirmation ──────────────────────────────────
        if (newState.confirmed && newState.lastConfirmed.isNotBlank() && newState.lastConfirmed != "SPACE") {
            val confirmedLabel = newState.lastConfirmed

            if (currentMode == RecognitionMode.ISL) {
                // For ISL word-level signs, push to NLP sentence buffer
                val bodyAnchor = BodySegmentation.detectActiveSegment(hands, activePose)?.id ?: ""
                val token = GlossToken(
                    label = confirmedLabel,
                    bodyAnchor = bodyAnchor,
                    timestamp = now,
                    confidence = newState.confidence
                )
                sentenceBuffer.push(token)

                // Update live pending gloss chips in UI and synthesize live preview sentence
                val pending = sentenceBuffer.peek().map { it.label }
                val livePreview = IslGrammarSynthesizer.synthesize(sentenceBuffer.peek())
                nlpState = nlpState.copy(
                    pendingGlosses = pending,
                    synthesizedSentence = livePreview
                )
            } else {
                // ASL / Fingerspelling: speak each letter individually as before
                speechManager.speak(confirmedLabel)
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stopCamera()
        handHelper?.close()
        poseHelper?.close()
        speechManager.shutdown()
    }
}
