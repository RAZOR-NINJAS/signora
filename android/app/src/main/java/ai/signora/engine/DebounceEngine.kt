package ai.signora.engine

import ai.signora.models.DebounceState
import ai.signora.models.RecognitionResult

/**
 * Temporal debounce and dwell locking engine for fluid sign language transcription.
 *
 * Implements smoothing, dwell progress tracking, repeat cooldown, and transcript retention.
 */
class DebounceEngine(
    var dwellMs: Long = 600L,
    var threshold: Float = 0.52f,
    private val spaceDwellMs: Long = 500L,
    private val repeatCooldownMs: Long = 550L
) {
    private var lastCandidate: String = ""
    private var dwellStartMs: Long = 0L
    private var lastConfirmed: String = ""
    private var lastConfirmedTimeMs: Long = 0L
    private val transcriptBuilder = StringBuilder()

    fun update(
        result: RecognitionResult?,
        nowMs: Long = System.currentTimeMillis()
    ): DebounceState {
        if (result == null || result.confidence < threshold) {
            lastCandidate = ""
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

        val candidate = result.label
        val conf = result.confidence
        var confirmed = false

        val requiredDwell = if (candidate == "SPACE") spaceDwellMs else dwellMs

        if (candidate != lastCandidate) {
            lastCandidate = candidate
            dwellStartMs = nowMs
        }

        val elapsed = nowMs - dwellStartMs
        val dwellProgress = (elapsed.toFloat() / requiredDwell.toFloat()).coerceIn(0f, 1f)

        if (dwellProgress >= 1.0f) {
            val cooldownOk = (nowMs - lastConfirmedTimeMs) >= repeatCooldownMs
            val isNewChar = candidate != lastConfirmed

            if (isNewChar || cooldownOk) {
                confirmed = true
                lastConfirmed = candidate
                lastConfirmedTimeMs = nowMs
                dwellStartMs = nowMs // Reset for repeat

                if (candidate == "SPACE") {
                    if (transcriptBuilder.isNotEmpty() && !transcriptBuilder.endsWith(" ")) {
                        transcriptBuilder.append(" ")
                    }
                } else {
                    transcriptBuilder.append(candidate)
                }
            }
        }

        return DebounceState(
            candidate = candidate,
            confidence = conf,
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
