package ai.signora.engine

import ai.signora.models.DebounceState
import ai.signora.models.RecognitionResult

/**
 * Temporal debounce and dwell locking engine for fluid sign language transcription.
 *
 * Implements smoothing, dwell progress tracking, repeat cooldown, and transcript retention.
 */
class DebounceEngine(
    var dwellMs: Long = 1000L,
    var threshold: Float = 0.52f,
    private val smoothMs: Long = 300L,
    private val spaceDwellMs: Long = 800L,
    private val repeatCooldownMs: Long = 550L
) {
    private data class HistoryEntry(val timestampMs: Long, val label: String, val confidence: Float)
    private val history = ArrayDeque<HistoryEntry>()

    private var activeDwellCandidate: String = ""
    private var dwellStartMs: Long = 0L
    private var lastConfirmed: String = ""
    private var lastConfirmedTimeMs: Long = 0L
    private val transcriptBuilder = StringBuilder()

    fun update(
        result: RecognitionResult?,
        nowMs: Long = System.currentTimeMillis()
    ): DebounceState {
        if (result != null && result.confidence >= threshold) {
            history.addLast(HistoryEntry(nowMs, result.label, result.confidence))
        }

        // Evict older entries outside smoothing window
        val cutoff = nowMs - smoothMs
        while (history.isNotEmpty() && history.first().timestampMs < cutoff) {
            history.removeFirst()
        }

        // Find dominant label in smoothing window
        var dominant: String? = null
        var maxWeight = 0f
        var dominantConf = 0f
        if (history.isNotEmpty()) {
            val weights = mutableMapOf<String, Float>()
            for (entry in history) {
                weights[entry.label] = (weights[entry.label] ?: 0f) + entry.confidence
            }
            for ((lbl, weight) in weights) {
                if (weight > maxWeight) {
                    maxWeight = weight
                    dominant = lbl
                }
            }
            dominantConf = history.lastOrNull { it.label == dominant }?.confidence ?: (result?.confidence ?: 0f)
        }

        if (dominant == null) {
            activeDwellCandidate = ""
            dwellStartMs = 0L
            return DebounceState(
                candidate = "",
                confidence = 0f,
                dwellProgress = 0f,
                confirmed = false,
                lastConfirmed = lastConfirmed,
                transcript = transcriptBuilder.toString()
            )
        }

        val requiredDwell = if (dominant == "SPACE") spaceDwellMs else dwellMs
        var confirmed = false

        if (dominant != activeDwellCandidate) {
            activeDwellCandidate = dominant
            dwellStartMs = nowMs
        }

        val elapsed = nowMs - dwellStartMs
        val dwellProgress = (elapsed.toFloat() / requiredDwell.toFloat()).coerceIn(0f, 1f)

        if (dwellProgress >= 1.0f) {
            val cooldownOk = (nowMs - lastConfirmedTimeMs) >= repeatCooldownMs
            val isNewChar = dominant != lastConfirmed

            if (isNewChar || cooldownOk) {
                confirmed = true
                lastConfirmed = dominant
                lastConfirmedTimeMs = nowMs
                dwellStartMs = nowMs // Reset for repeat

                if (dominant == "SPACE") {
                    if (transcriptBuilder.isNotEmpty() && !transcriptBuilder.endsWith(" ")) {
                        transcriptBuilder.append(" ")
                    }
                } else {
                    transcriptBuilder.append(dominant)
                }
            }
        }

        return DebounceState(
            candidate = dominant,
            confidence = dominantConf,
            dwellProgress = dwellProgress,
            confirmed = confirmed,
            lastConfirmed = lastConfirmed,
            transcript = transcriptBuilder.toString()
        )
    }

    fun backspace(): String {
        if (transcriptBuilder.isNotEmpty()) {
            transcriptBuilder.deleteCharAt(transcriptBuilder.length - 1)
        }
        return transcriptBuilder.toString()
    }

    fun clear(): String {
        transcriptBuilder.setLength(0)
        lastConfirmed = ""
        return ""
    }

    fun getTranscript(): String = transcriptBuilder.toString()
}
