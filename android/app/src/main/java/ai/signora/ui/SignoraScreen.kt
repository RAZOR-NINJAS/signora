package ai.signora.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import ai.signora.models.*
import ai.signora.nlp.EnglishToIslResult
import ai.signora.recovery.AutoAssetManager
import ai.signora.speech.SpeechState
import ai.signora.ui.components.AppDestination
import ai.signora.ui.components.AppNavigationBar
import ai.signora.ui.components.AppNavigationRail
import ai.signora.ui.components.SignoraIntro
import ai.signora.ui.screens.LearnScreen
import ai.signora.ui.screens.SettingsScreen
import ai.signora.ui.screens.TranslateScreen
import ai.signora.ui.theme.AppThemeMode

@Composable
fun SignoraScreen(
    previewView: PreviewView,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    debounceState: DebounceState,
    handLandmarks: List<HandLandmarks>,
    poseLandmarks: PoseLandmarks? = null,
    currentMode: RecognitionMode,
    dwellProfile: DwellProfile,
    isMuted: Boolean,
    isFrontCamera: Boolean,
    isTorchOn: Boolean = false,
    isPaused: Boolean = false,
    fps: Float = 30f,
    isDark: Boolean = true,
    themeMode: AppThemeMode = AppThemeMode.DARK,
    selectedPalette: ai.signora.ui.theme.AppPalette = ai.signora.ui.theme.AppPalette.WALLPAPER,
    dynamicColor: Boolean = true,
    autoCollapseEnabled: Boolean = true,
    latestFrameId: Long = 0L,
    customDwellMs: Long = 1000L,
    confidenceThreshold: Float = 0.40f,
    minMargin: Float = 0.05f,
    cameraResolution: Pair<Int, Int> = Pair(480, 640),
    assetStatus: AutoAssetManager.AssetStatus,
    nlpState: NlpState = NlpState(),
    speechState: SpeechState = SpeechState.Idle,
    speechPartialText: String = "",
    speechSoundLevel: Float = 0f,
    speechTranslationResult: EnglishToIslResult? = null,
    onStartSpeechListening: () -> Unit = {},
    onStopSpeechListening: () -> Unit = {},
    onManualTranslateText: (String) -> Unit = {},
    onModeChange: (RecognitionMode) -> Unit,
    onDwellProfileChange: (DwellProfile) -> Unit,
    onCustomDwellChange: (Long) -> Unit = {},
    onConfidenceThresholdChange: (Float) -> Unit = {},
    onMinMarginChange: (Float) -> Unit = {},
    onAutoCollapseChange: (Boolean) -> Unit = {},
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleTorch: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onPaletteChange: (ai.signora.ui.theme.AppPalette) -> Unit = {},
    onDynamicColorChange: (Boolean) -> Unit = {},
    onTestVoice: () -> Unit = {},
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSpace: () -> Unit,
    onSpeak: () -> Unit,
    onReloadModels: () -> Unit = {},
    onAttachPreview: (PreviewView) -> Unit = {}
) {
    var currentDestination by remember { mutableStateOf(AppDestination.TRANSLATE) }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var showIntro by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Navigation Rail for Tablets / Landscape
            if (isWideScreen) {
                AppNavigationRail(
                    currentDestination = currentDestination,
                    onNavigate = { currentDestination = it }
                )
            }

            Scaffold(
                bottomBar = {
                    if (!isWideScreen) {
                        AppNavigationBar(
                            currentDestination = currentDestination,
                            onNavigate = { currentDestination = it }
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.weight(1f)
            ) { innerPadding ->
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                            initialOffsetX = { it / 5 * dir }
                        ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(260))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(190, easing = FastOutLinearInEasing),
                                targetOffsetX = { -it / 8 * dir }
                            ) + fadeOut(tween(140)))
                    },
                    label = "ScreenTransition"
                ) { destination ->
                    when (destination) {
                        AppDestination.TRANSLATE -> {
                            TranslateScreen(
                                previewView = previewView,
                                hasCameraPermission = hasCameraPermission,
                                onRequestCameraPermission = onRequestCameraPermission,
                                debounceState = debounceState,
                                handLandmarks = handLandmarks,
                                poseLandmarks = poseLandmarks,
                                currentMode = currentMode,
                                isMuted = isMuted,
                                isFrontCamera = isFrontCamera,
                                isTorchOn = isTorchOn,
                                isPaused = isPaused,
                                fps = fps,
                                isDark = isDark,
                                autoCollapseEnabled = autoCollapseEnabled,
                                latestFrameId = latestFrameId,
                                assetStatus = assetStatus,
                                nlpState = nlpState,
                                onModeChange = onModeChange,
                                onToggleMute = onToggleMute,
                                onToggleCamera = onToggleCamera,
                                onToggleTorch = onToggleTorch,
                                onTogglePause = onTogglePause,
                                onToggleTheme = onToggleTheme,
                                onOpenSettings = { currentDestination = AppDestination.SETTINGS },
                                onBackspace = onBackspace,
                                onClear = onClear,
                                onSpace = onSpace,
                                onSpeak = onSpeak,
                                cameraResolution = cameraResolution,
                                bottomBarPadding = innerPadding.calculateBottomPadding(),
                                speechState = speechState,
                                speechPartialText = speechPartialText,
                                speechSoundLevel = speechSoundLevel,
                                speechTranslationResult = speechTranslationResult,
                                onStartSpeechListening = onStartSpeechListening,
                                onStopSpeechListening = onStopSpeechListening,
                                onManualTranslateText = onManualTranslateText,
                                onAttachPreview = onAttachPreview
                            )
                        }


                        AppDestination.STUDIO -> {
                            ai.signora.ui.screens.TrainStudioScreen(
                                previewView = previewView,
                                hands = handLandmarks,
                                pose = poseLandmarks,
                                onToggleCamera = onToggleCamera,
                                cameraResolution = cameraResolution,
                                onReloadModels = onReloadModels,
                                bottomBarPadding = innerPadding.calculateBottomPadding(),
                                onAttachPreview = onAttachPreview
                            )
                        }

                        AppDestination.GAME -> {
                            ai.signora.ui.screens.DinoGameScreen(
                                previewView = previewView,
                                debounceState = debounceState,
                                handLandmarks = handLandmarks,
                                currentMode = currentMode,
                                onModeChange = onModeChange,
                                onToggleCamera = onToggleCamera,
                                onAttachPreview = onAttachPreview
                            )
                        }

                        AppDestination.LEARN -> {
                            LearnScreen(
                                currentMode = currentMode,
                                onModeChange = onModeChange,
                                debounceState = debounceState,
                                previewView = previewView
                            )
                        }

                        AppDestination.SETTINGS -> {
                            SettingsScreen(
                                themeMode = themeMode,
                                selectedPalette = selectedPalette,
                                dynamicColor = dynamicColor,
                                dwellProfile = dwellProfile,
                                customDwellMs = customDwellMs,
                                confidenceThreshold = confidenceThreshold,
                                minMargin = minMargin,
                                isMuted = isMuted,
                                isFrontCamera = isFrontCamera,
                                autoCollapseEnabled = autoCollapseEnabled,
                                onThemeModeChange = onThemeModeChange,
                                onPaletteChange = onPaletteChange,
                                onDynamicColorChange = onDynamicColorChange,
                                onDwellProfileChange = onDwellProfileChange,
                                onCustomDwellChange = onCustomDwellChange,
                                onConfidenceThresholdChange = onConfidenceThresholdChange,
                                onMinMarginChange = onMinMarginChange,
                                onAutoCollapseChange = onAutoCollapseChange,
                                onToggleMute = onToggleMute,
                                onToggleCamera = onToggleCamera,
                                onTestVoice = onTestVoice
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showIntro,
            enter = EnterTransition.None,
            exit = fadeOut(tween(360)) + scaleOut(targetScale = 1.05f, animationSpec = tween(360)),
            label = "IntroOverlay"
        ) {
            SignoraIntro(onFinished = { showIntro = false })
        }
    }
}
