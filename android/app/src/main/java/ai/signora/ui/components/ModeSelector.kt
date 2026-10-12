package ai.signora.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.signora.models.RecognitionMode
import ai.signora.ui.theme.SignoraTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModeSelector(
    currentMode: RecognitionMode,
    onModeSelected: (RecognitionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.widthIn(min = 160.dp, max = 220.dp)
    ) {
        SegmentedButton(
            selected = currentMode == RecognitionMode.ASL,
            onClick = { onModeSelected(RecognitionMode.ASL) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = {
                Text(
                    text = "ASL",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        SegmentedButton(
            selected = currentMode == RecognitionMode.ISL,
            onClick = { onModeSelected(RecognitionMode.ISL) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = {
                Text(
                    text = "ISL",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}

@Preview(name = "ModeSelector Light")
@Composable
private fun ModeSelectorPreviewLight() {
    SignoraTheme(dynamicColor = false) {
        ModeSelector(
            currentMode = RecognitionMode.ASL,
            onModeSelected = {}
        )
    }
}

@Preview(name = "ModeSelector Dark")
@Composable
private fun ModeSelectorPreviewDark() {
    SignoraTheme(dynamicColor = false) {
        ModeSelector(
            currentMode = RecognitionMode.ISL,
            onModeSelected = {}
        )
    }
}
