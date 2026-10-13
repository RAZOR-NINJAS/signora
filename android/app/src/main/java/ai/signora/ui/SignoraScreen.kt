package ai.signora.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import ai.signora.models.*
import ai.signora.recovery.AutoAssetManager
import ai.signora.ui.components.AppDestination
import ai.signora.ui.components.AppNavigationBar
import ai.signora.ui.components.AppNavigationRail
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
    currentMode: RecognitionMode,
    dwellProfile: DwellProfile,
    isMuted: Boolean,
    isFrontCamera: Boolean,
    isTorchOn: Boolean = false,
    isPaused: Boolean = false,
    fps: Float = 30f,
    isDark: Boolean = true,
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = true,
    autoCollapseEnabled: Boolean = true,
    latestFrameId: Long = 0L,
    customDwellMs: Long = 1000L,
    confidenceThreshold: Float = 0.70f,
    minMargin: Float = 0.10f,
    assetStatus: AutoAssetManager.AssetStatus,
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
    onDynamicColorChange: (Boolean) -> Unit = {},
    onTestVoice: () -> Unit = {},
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSpace: () -> Unit,
    onSpeak: () -> Unit
) {
    var currentDestination by remember { mutableStateOf(AppDestination.TRANSLATE) }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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
                // In Translate screen, navigation bar is embedded in the expanded bottom deck or hidden during translate
                if (!isWideScreen && currentDestination != AppDestination.TRANSLATE) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (!isWideScreen && currentDestination != AppDestination.TRANSLATE) innerPadding.calculateBottomPadding() else 0.dp)
            ) {
                when (currentDestination) {
                    AppDestination.TRANSLATE -> {
                        TranslateScreen(
                            previewView = previewView,
                            hasCameraPermission = hasCameraPermission,
                            onRequestCameraPermission = onRequestCameraPermission,
                            debounceState = debounceState,
                            handLandmarks = handLandmarks,
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
                            onModeChange = onModeChange,
                            onToggleMute = onToggleMute,
                            onToggleCamera = onToggleCamera,
                            onToggleTorch = onToggleTorch,
                            onTogglePause = onTogglePause,
                            onToggleTheme = onToggleTheme,
                            onBackspace = onBackspace,
                            onClear = onClear,
                            onSpace = onSpace,
                            onSpeak = onSpeak
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
                            dynamicColor = dynamicColor,
                            dwellProfile = dwellProfile,
                            customDwellMs = customDwellMs,
                            confidenceThreshold = confidenceThreshold,
                            minMargin = minMargin,
                            isMuted = isMuted,
                            isFrontCamera = isFrontCamera,
                            autoCollapseEnabled = autoCollapseEnabled,
                            onThemeModeChange = onThemeModeChange,
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
}
