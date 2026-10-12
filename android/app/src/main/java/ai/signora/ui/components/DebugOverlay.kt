package ai.signora.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.signora.models.DebounceState
import ai.signora.models.HandLandmarks
import ai.signora.ui.theme.AppTheme
import ai.signora.ui.theme.TelemetryMonospace

@Composable
fun DebugOverlay(
    debounceState: DebounceState,
    delegateName: String,
    handLandmarks: List<HandLandmarks>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(AppTheme.spacing.medium)
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "NEURAL TELEMETRY OVERLAY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("CLOSE", style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Inference FPS: ${debounceState.fps.toInt()} FPS", style = TelemetryMonospace)
                Text("Forward Pass: ${String.format(java.util.Locale.US, "%.2f", debounceState.inferenceMs)} ms", style = TelemetryMonospace)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Acceleration: $delegateName", style = TelemetryMonospace)
                Text("Hands Tracked: ${handLandmarks.size}", style = TelemetryMonospace)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dwell Status: ${debounceState.status.name}", style = TelemetryMonospace)
                Text("Dwell Margin: ${String.format(java.util.Locale.US, "%.2f", debounceState.margin)}", style = TelemetryMonospace)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "TOP-3 PROBABILITIES:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            debounceState.top3.forEachIndexed { index, (label, prob) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("#${index + 1} [$label]", style = TelemetryMonospace)
                    Text("${(prob * 100).toInt()}% (${String.format(java.util.Locale.US, "%.4f", prob)})", style = TelemetryMonospace)
                }
            }
        }
    }
}
