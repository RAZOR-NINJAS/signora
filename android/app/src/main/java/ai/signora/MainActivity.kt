package ai.signora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import ai.signora.ui.SignoraScreen
import ai.signora.ui.SignoraTheme
import ai.signora.vision.HandLandmarkerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var assetManager: AutoAssetManager
    private lateinit var speechManager: SpeechManager
    private var cameraManager: CameraXManager? = null
    private var handHelper: HandLandmarkerHelper? = null

    private var aslMlp: SignoraMLP? = null
    private var islMlp: SignoraMLP? = null

    private val debounceEngine = DebounceEngine()

    private var hasCameraPermission by mutableStateOf(false)
    private var assetStatus by mutableStateOf(AutoAssetManager.AssetStatus(isReady = false, message = "Initializing..."))
    private var currentMode by mutableStateOf(RecognitionMode.ASL)
    private var dwellProfile by mutableStateOf(DwellProfile.STANDARD)
    private var isMuted by mutableStateOf(false)
    private var isFrontCamera by mutableStateOf(true)
    private var debounceState by mutableStateOf(DebounceState())
    private var activeLandmarks by mutableStateOf<List<HandLandmarks>>(emptyList())

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 30f

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            setupVisionPipeline()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        assetManager = AutoAssetManager(this)
        speechManager = SpeechManager(this)

        val previewView = PreviewView(this)

        setContent {
            SignoraTheme {
                SignoraScreen(
                    previewView = previewView,
                    debounceState = debounceState,
                    handLandmarks = activeLandmarks,
                    currentMode = currentMode,
                    dwellProfile = dwellProfile,
                    isMuted = isMuted,
                    isFrontCamera = isFrontCamera,
                    assetStatus = assetStatus,
                    onModeChange = { newMode -> currentMode = newMode },
                    onDwellProfileChange = { newProfile ->
                        dwellProfile = newProfile
                        debounceEngine.dwellMs = newProfile.dwellMs
                    },
                    onToggleMute = {
                        isMuted = !isMuted
                        speechManager.isMuted = isMuted
                    },
                    onToggleCamera = {
                        cameraManager?.toggleCamera()
                        isFrontCamera = cameraManager?.isFrontCamera ?: true
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

        // Initialize and self-heal model assets in background
        lifecycleScope.launch {
            val ok = assetManager.ensureModelAssets { status ->
                assetStatus = status
            }
            if (ok) {
                loadNeuralEngines()
                checkCameraPermission()
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
        val landmarkerTaskFile = assetManager.getModelFile("hand_landmarker.task")

        handHelper = HandLandmarkerHelper(
            context = this,
            modelFile = landmarkerTaskFile,
            onResults = { hands, timestamp ->
                onHandsDetected(hands, timestamp)
            },
            onError = { err ->
                // Non-critical logging
            }
        )

        val preview = PreviewView(this)
        cameraManager = CameraXManager(
            context = this,
            lifecycleOwner = this,
            previewView = preview,
            onFrame = { mpImage, frameTime ->
                handHelper?.detectLiveStream(mpImage, frameTime)
            }
        )
        cameraManager?.startCamera()
    }

    private fun onHandsDetected(hands: List<HandLandmarks>, timestamp: Long) {
        activeLandmarks = hands

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
            return
        }

        // Extract features based on active mode
        val activeMlp = if (currentMode == RecognitionMode.ASL) aslMlp else islMlp
        if (activeMlp == null) return

        val feats = if (currentMode == RecognitionMode.ASL) {
            FeatureExtractor.extractAslFeatures(hands[0].points, hands[0].isRightHand)
        } else {
            FeatureExtractor.extractIslFeatures(hands)
        }

        // Run sub-ms neural forward pass
        val result = activeMlp.predict(feats)

        // Debounce and dwell tracking
        val newState = debounceEngine.update(result, now).copy(fps = currentFps)
        debounceState = newState

        // Vocalize confirmed letters/words
        if (newState.confirmed && newState.lastConfirmed.isNotBlank() && newState.lastConfirmed != "SPACE") {
            speechManager.speak(newState.lastConfirmed)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stopCamera()
        handHelper?.close()
        speechManager.shutdown()
    }
}
