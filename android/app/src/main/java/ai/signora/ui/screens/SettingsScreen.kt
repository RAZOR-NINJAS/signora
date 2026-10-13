package ai.signora.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.signora.models.DwellProfile
import ai.signora.ui.components.SectionHeader
import ai.signora.ui.components.SettingsItem
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.AppThemeMode
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    themeMode: AppThemeMode,
    dynamicColor: Boolean,
    dwellProfile: DwellProfile,
    customDwellMs: Long,
    confidenceThreshold: Float,
    minMargin: Float,
    isMuted: Boolean,
    isFrontCamera: Boolean,
    autoCollapseEnabled: Boolean = true,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDwellProfileChange: (DwellProfile) -> Unit,
    onCustomDwellChange: (Long) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit,
    onMinMarginChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onAutoCollapseChange: (Boolean) -> Unit = {},
    onTestVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = AppTheme.spacing.medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppTheme.spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Engine tuning, themes, and accessibility settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Section: Appearance & Theme
        SectionHeader(title = "Appearance & Theme")

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(AppTheme.spacing.medium)) {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AppTheme.spacing.small))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppThemeMode.values().forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppThemeMode.values().size
                            ),
                            label = {
                                Text(
                                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(AppTheme.spacing.medium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Material You Dynamic Color",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Extract accent palettes from wallpaper (Android 12+)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                }
            }
        }

        // Section: Gesture Debounce & Dwell Timers
        SectionHeader(title = "Dwell Duration & Smoothing")

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)
            ) {
                Text(
                    text = "Dwell Timing Presets",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                                    text = profile.label,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }

                // Custom Dwell Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Fine-Grained Dwell Duration",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.3fs", customDwellMs / 1000f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = customDwellMs.toFloat(),
                        onValueChange = { onCustomDwellChange(it.toLong()) },
                        valueRange = 300f..3000f,
                        steps = 27
                    )
                }

                // Auto-Collapse Bottom Bar Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Collapse on Hand Detection",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Collapses dock into compact tab (150ms enter, 800ms exit hysteresis)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCollapseEnabled,
                        onCheckedChange = onAutoCollapseChange
                    )
                }

                // Confidence Threshold Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Minimum Confidence Threshold",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(confidenceThreshold * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = confidenceThreshold,
                        onValueChange = onConfidenceThresholdChange,
                        valueRange = 0.50f..0.95f,
                        steps = 9
                    )
                }

                // Minimum Margin Gating Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Top-1 Margin Hysteresis",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", minMargin),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = minMargin,
                        onValueChange = onMinMarginChange,
                        valueRange = 0.05f..0.30f,
                        steps = 5
                    )
                }
            }
        }

        // Section: Audio & TTS Synthesizer
        SectionHeader(title = "Speech & Vocalizer")

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Speak Confirmed Signs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vocalize completed letters via Text-To-Speech",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !isMuted,
                        onCheckedChange = { onToggleMute() }
                    )
                }

                FilledTonalButton(
                    onClick = onTestVoice,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AppTheme.spacing.small))
                    Text("Test Vocalizer Audio")
                }
            }
        }

        // Section: Hardware & Vision
        SectionHeader(title = "Hardware & Vision")

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Active Lens",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isFrontCamera) "Front Camera (Selfie mirror)" else "Back Camera (Rear lens)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleCamera,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Flip Camera")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CameraX Target Resolution",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1280 x 720 HD",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Landmark Smoothing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "One-Euro 3D Filter",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AppTheme.semantic.success
                    )
                }
            }
        }

        // Section: Architecture & Model Ethics
        SectionHeader(title = "Architecture & Ethics")

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
            ) {
                Text(
                    text = "Signora Neural Engine v2.0",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• ASL Classifier: Trained for 26 alphabet characters (A–Z) and Space.\n" +
                            "• ISL Classifier: Honest disclosure — currently trained for A, B, C, D, and Space (5 of 26 letters). Remaining classes in active development.\n" +
                            "• Hand Landmarker: Google MediaPipe Tasks Vision on-device tracker (21 3D landmarks).\n" +
                            "• Privacy Guarantee: 100% on-device local computation. Zero external network requests, zero telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.spacing.large))
    }
}
