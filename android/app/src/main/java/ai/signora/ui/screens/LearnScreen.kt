package ai.signora.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.signora.models.DebounceState
import ai.signora.models.RecognitionMode
import ai.signora.ui.components.ActiveSignDisplay
import ai.signora.ui.components.ModeSelector
import ai.signora.ui.theme.AppTheme

data class SignGuide(
    val letter: String,
    val posture: String,
    val tips: String
)

private val ASL_GUIDES = listOf(
    SignGuide("A", "Make a fist with thumb resting vertically against index finger", "Keep thumb upright alongside fingers"),
    SignGuide("B", "Extend 4 fingers upright together with thumb folded across palm", "Keep fingers straight and touching"),
    SignGuide("C", "Curve all fingers and thumb to form a C shape", "Maintain a relaxed crescent curve"),
    SignGuide("D", "Point index finger straight up, thumb and other fingers form a ring", "Only index finger points up"),
    SignGuide("E", "Curl all fingers down to touch thumb tip", "Keep fingernails touching thumb"),
    SignGuide("F", "Touch index and thumb tips together, other 3 fingers straight up", "Opposite posture of D"),
    SignGuide("G", "Point index horizontally, thumb parallel alongside it", "Hold like pointing sideways"),
    SignGuide("H", "Extend index and middle fingers together horizontally", "Keep middle and index glued together"),
    SignGuide("I", "Extend pinky straight up, all other fingers closed into a fist", "Classic pinky up gesture"),
    SignGuide("K", "Extend index and middle in V, thumb between them resting on middle", "Keep wrist straight"),
    SignGuide("L", "Extend thumb and index at right angles forming an L", "Make sure angle is 90 degrees"),
    SignGuide("M", "Tuck thumb under index, middle, and ring fingers", "Three finger knuckles over thumb"),
    SignGuide("N", "Tuck thumb under index and middle fingers", "Two finger knuckles over thumb"),
    SignGuide("O", "Touch all fingertips to thumb tip forming an O", "Keep opening circular and visible"),
    SignGuide("P", "Point index downwards, middle horizontally, thumb between", "Like letter K pointing down"),
    SignGuide("Q", "Point index and thumb downwards together", "Like letter G pointing down"),
    SignGuide("R", "Cross middle finger over index finger", "Like making a good luck sign"),
    SignGuide("S", "Make a tight fist with thumb resting across front of fingers", "Thumb over knuckles distinguishes from A"),
    SignGuide("T", "Tuck thumb between index and middle finger", "One finger knuckle over thumb"),
    SignGuide("U", "Hold index and middle fingers straight up together", "Fingers straight and touching"),
    SignGuide("V", "Hold index and middle fingers apart in a V shape", "Peace sign gesture"),
    SignGuide("W", "Extend index, middle, and ring fingers upright apart", "Form a clean three-prong fork"),
    SignGuide("X", "Bend index finger into a hook shape", "Other fingers closed in fist"),
    SignGuide("Y", "Extend thumb and pinky outwards while closing middle fingers", "Hang loose / phone sign"),
    SignGuide("SPACE", "Open flat palm held horizontally", "Neutral open palm gesture")
)

private val ISL_GUIDES = listOf(
    SignGuide("A", "Make a fist with thumb resting alongside index finger", "Recognized in current 5-letter model"),
    SignGuide("B", "Extend 4 fingers upright together with thumb folded across palm", "Recognized in current 5-letter model"),
    SignGuide("C", "Curve all fingers and thumb to form a C shape", "Recognized in current 5-letter model"),
    SignGuide("D", "Point index finger straight up, thumb and remaining fingers in a ring", "Recognized in current 5-letter model"),
    SignGuide("SPACE", "Open flat palm held horizontally", "Recognized in current 5-letter model")
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LearnScreen(
    currentMode: RecognitionMode,
    onModeChange: (RecognitionMode) -> Unit,
    debounceState: DebounceState,
    previewView: PreviewView? = null,
    modifier: Modifier = Modifier
) {
    val guides = if (currentMode == RecognitionMode.ISL) ISL_GUIDES else ASL_GUIDES
    var selectedLetter by remember(currentMode) { mutableStateOf(guides.first().letter) }
    var practiceMode by remember { mutableStateOf(false) }

    val activeGuide = guides.find { it.letter == selectedLetter } ?: guides.first()
    val isMatched = debounceState.candidate.equals(selectedLetter, ignoreCase = true)
    val isMastered = isMatched && debounceState.dwellProgress >= 1f

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = AppTheme.spacing.medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppTheme.spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sign Academy",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Master signs interactively with live vision feedback",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Mode Selector
        ModeSelector(
            currentMode = currentMode,
            onModeSelected = onModeChange,
            modifier = Modifier.fillMaxWidth()
        )

        // ISL Disclosure Banner
        if (currentMode == RecognitionMode.ISL) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = "ISL currently supports A, B, C, D and Space (5 of 26 letters). Remaining alphabet under training.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Horizontal Alphabet Bar
        Text(
            text = "SELECT SIGN TO PRACTICE",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
        ) {
            items(guides) { guide ->
                val isSelected = guide.letter == selectedLetter
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable { selectedLetter = guide.letter }
                        .height(48.dp)
                        .padding(horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = guide.letter,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // Letter Posture Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeGuide.letter,
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (isMastered) {
                        Surface(
                            shape = CircleShape,
                            color = AppTheme.semantic.successContainer,
                            contentColor = AppTheme.semantic.onSuccessContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("MASTERED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                Text(
                    text = activeGuide.posture,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Tip: ${activeGuide.tips}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(AppTheme.spacing.medium)
                    )
                }

                // Interactive Practice Toggle
                FilledTonalButton(
                    onClick = { practiceMode = !practiceMode },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AppTheme.spacing.small))
                    Text(if (practiceMode) "Hide Practice Camera" else "Practice with Live Camera")
                }
            }
        }

        // Live Practice Camera Window
        AnimatedVisibility(
            visible = practiceMode && previewView != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppTheme.spacing.medium)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Sign: $selectedLetter",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (isMatched) "Gesture Detected!" else "Show hand sign...",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMatched) AppTheme.semantic.success else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(AppTheme.spacing.small))

                    // Dwell Progress Bar towards Mastery
                    LinearWavyProgressIndicator(
                        progress = { if (isMatched) debounceState.dwellProgress else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = if (isMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(AppTheme.spacing.small))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        if (previewView != null) {
                            AndroidView(
                                factory = { _ ->
                                    (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.spacing.large))
    }
}
