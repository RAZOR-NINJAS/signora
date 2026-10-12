package ai.signora.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.SignoraTheme
import ai.signora.ui.theme.TranscriptMonospace

@Composable
fun TranscriptField(
    transcript: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.spacing.medium, vertical = AppTheme.spacing.small)
        ) {
            // Header Row: Label & Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSCRIPT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (transcript.isNotBlank()) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(transcript))
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Transcript",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scrollable Content Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 96.dp)
                    .verticalScroll(scrollState),
                contentAlignment = Alignment.CenterStart
            ) {
                if (transcript.isNotBlank()) {
                    Text(
                        text = transcript,
                        style = TranscriptMonospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Start signing to transcribe...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Preview(name = "TranscriptField Light")
@Composable
private fun TranscriptFieldPreviewLight() {
    SignoraTheme(dynamicColor = false) {
        TranscriptField(transcript = "HELLO WORLD")
    }
}

@Preview(name = "TranscriptField Empty Dark")
@Composable
private fun TranscriptFieldPreviewDark() {
    SignoraTheme(dynamicColor = false) {
        TranscriptField(transcript = "")
    }
}
