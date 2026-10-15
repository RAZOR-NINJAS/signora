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
import ai.signora.ml.FeatureExtractor
import ai.signora.ml.SignoraMLP
import ai.signora.models.*
import ai.signora.recovery.AutoAssetManager
import ai.signora.tts.SpeechManager
import ai.signora.profiler.TrackingProfiler
import ai.signora.ui.SignoraScreen
import ai.signora.ui.theme.AppThemeMode
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

    private val debounceEngine = DebounceEngine()

    private var hasCameraPermission by mutableStateOf(false)
    private var assetStatus by mutableStateOf(AutoAssetManager.AssetStatus(isReady = false, message = "Initializing..."))
    private var currentMode by mutableStateOf(RecognitionMode.ASL)
    private var dwellProfile by mutableStateOf(DwellProfile.STANDARD)
    private var customDwellMs by mutableStateOf(1000L)
    private var confidenceThreshold by mutableStateOf(0.70f)
    private var minMargin by mutableStateOf(0.10f)
    private var isMuted by mutableStateOf(false)
    private var isFrontCamera by mutableStateOf(true)
    private var isTorchOn by mutableStateOf(false)
    private var isPaused by mutableStateOf(false)
    private var autoCollapseEnabled by mutableStateOf(true)
    private var latestFrameId by mutableStateOf(0L)
    private var themeMode by mutableStateOf(AppThemeMode.DARK)
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
                    dynamicColor = dynamicColorEnabled,
                    autoCollapseEnabled = autoCollapseEnabled,
                    latestFrameId = latestFrameId,
                    customDwellMs = customDwellMs,
                    confidenceThreshold = confidenceThreshold,
                    minMargin = minMargin,
                    assetStatus = assetStatus,
                    onModeChange = { newMode -> currentMode = newMode },
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
                    onDynamicColorChange = { dynamicColorEnabled = it },
                    onTestVoice = {
                        speechManager.speak("Testing Unspoken Neural Voice synthesizer")
                    },
                    onBackspace = {
                        debounceEngine.backspace()
                        debounceState = debounceState.copy(transcript = debounceEngine.getTranscript())
                    },
                    onClear = {
                        debounceEngine.clear()
                        debounceState = debounceState.copy(transcript = "")
                    },
                    onSpace = {
                        val state = debounceEngine.update(RecognitionResult("SPACE", 0.99f))
                        debounceState = state
                    },
                    onSpeak = {
                        speechManager.speak(debounceEngine.getTranscript())
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
        )
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
            FeatureExtractor.extractAslFeatures(hands[0].points, hands[0].isRightHand)
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

        // Vocalize confirmed letters/words
        if (newState.confirmed && newState.lastConfirmed.isNotBlank() && newState.lastConfirmed != "SPACE") {
            speechManager.speak(newState.lastConfirmed)
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
