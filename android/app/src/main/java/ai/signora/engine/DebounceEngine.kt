package ai.signora.engine

import ai.signora.models.DebounceState
import ai.signora.models.DwellStatus
import ai.signora.models.RecognitionResult

/**
 * Temporal debounce and dwell locking engine for fluid, high-accuracy sign language transcription.
 *
 * Implements:
 * 1. 7-frame majority voting with confusable-pair hysteresis (M/N, U/V, R/U, A/S)
 * 2. Minimum margin gating (top-1 prob - top-2 prob >= margin)
 * 3. 150ms dropout pause tolerance (brief occlusion won't reset dwell)
 * 4. Deliberate double-letter support (requires release or alternate gesture before repeat)
 * 5. Live-configurable dwell duration, confidence threshold, and margin
 */
class DebounceEngine(
    var dwellMs: Long = 1000L,
    var threshold: Float = 0.52f,
    var minMargin: Float = 0.10f,
    private val voteWindowSize: Int = 7,
    private val maxDropoutMs: Long = 150L,
    private val spaceDwellMs: Long = 800L
) {
    var confidenceThreshold: Float
        get() = threshold
        set(value) { threshold = value }
    // Sliding vote buffer of recent valid frame labels
    private val voteWindow = ArrayDeque<String>()

    // Dwell state tracking
    private var activeCandidate: String = ""
    private var dwellAccumulatedMs: Long = 0L
    private var lastFrameTimeMs: Long = 0L
    private var lastHandSeenTimeMs: Long = 0L

    // Repeat lock: must release before committing same letter again
    private var committedWaitingRelease: Boolean = false
    private var lastCommittedLetter: String = ""

    // Transcript state
    private val transcriptBuilder = StringBuilder()

    // Confusable pair hysteresis map
    private val confusablePairs = setOf(
        setOf("M", "N"),
        setOf("U", "V"),
        setOf("R", "U"),
        setOf("A", "S"),
        setOf("E", "S"),
        setOf("K", "P")
    )

    fun update(
        result: RecognitionResult?,
        nowMs: Long = System.currentTimeMillis()
    ): DebounceState {
        // 1. Handle Hand Presence / Loss
        if (result == null || result.confidence < threshold || result.margin < minMargin) {
            val timeSinceLastHand = if (lastHandSeenTimeMs > 0L) nowMs - lastHandSeenTimeMs else Long.MAX_VALUE

            if (timeSinceLastHand > maxDropoutMs) {
                // Extended loss: reset dwell progress and clear candidate
                activeCandidate = ""
                dwellAccumulatedMs = 0L
                committedWaitingRelease = false
                voteWindow.clear()
            }
            // If within dropout tolerance, dwellAccumulatedMs is paused (neither increases nor resets)

            lastFrameTimeMs = nowMs

            return DebounceState(
                candidate = activeCandidate,
                confidence = 0f,
                margin = 0f,
                dwellProgress = if (activeCandidate.isNotBlank()) {
                    val req = if (activeCandidate == "SPACE") spaceDwellMs else dwellMs
                    (dwellAccumulatedMs.toFloat() / req.toFloat()).coerceIn(0f, 1f)
                } else 0f,
                confirmed = false,
                lastConfirmed = lastCommittedLetter,
                transcript = transcriptBuilder.toString(),
                status = if (timeSinceLastHand > maxDropoutMs) DwellStatus.NO_HAND else DwellStatus.HOLDING
            )
        }

        // 2. Hand is actively detected and passes confidence & margin thresholds
        lastHandSeenTimeMs = nowMs
        val rawLabel = result.label

        // 3. Majority Voting with Hysteresis
        voteWindow.addLast(rawLabel)
        if (voteWindow.size > voteWindowSize) {
            voteWindow.removeFirst()
        }

        // Count votes in sliding window
        val voteCounts = mutableMapOf<String, Int>()
        for (lbl in voteWindow) {
            voteCounts[lbl] = (voteCounts[lbl] ?: 0) + 1
        }
        var dominantLabel = rawLabel
        var maxVotes = 0
        for ((lbl, count) in voteCounts) {
            if (count > maxVotes) {
                maxVotes = count
                dominantLabel = lbl
            }
        }

        // Apply hysteresis penalty if switching between confusable pairs
        if (activeCandidate.isNotBlank() && dominantLabel != activeCandidate) {
            val isConfusable = confusablePairs.any { it.contains(activeCandidate) && it.contains(dominantLabel) }
            if (isConfusable && maxVotes < (voteWindowSize - 1)) {
                // Suppress switch unless strongly voted (overcomes flicker)
                dominantLabel = activeCandidate
            }
        }

        // 4. Update Dwell Timer
        val dt = if (lastFrameTimeMs > 0L) (nowMs - lastFrameTimeMs).coerceIn(0L, 100L) else 0L
        lastFrameTimeMs = nowMs

        var confirmedThisFrame = false

        if (dominantLabel != activeCandidate) {
            // Letter changed: user switched gesture
            activeCandidate = dominantLabel
            dwellAccumulatedMs = 0L
            committedWaitingRelease = false
        } else {
            // Same letter held steadily
            if (!committedWaitingRelease) {
                dwellAccumulatedMs += dt
            }
        }

        val requiredDwell = if (activeCandidate == "SPACE") spaceDwellMs else dwellMs
        val dwellProgress = (dwellAccumulatedMs.toFloat() / requiredDwell.toFloat()).coerceIn(0f, 1f)

        // 5. Commit Condition
        if (dwellProgress >= 1.0f && !committedWaitingRelease) {
            confirmedThisFrame = true
            lastCommittedLetter = activeCandidate
            committedWaitingRelease = true // Lock until hand released or different sign made

            if (activeCandidate == "SPACE") {
                if (transcriptBuilder.isNotEmpty() && !transcriptBuilder.endsWith(" ")) {
                    transcriptBuilder.append(" ")
                }
            } else {
                transcriptBuilder.append(activeCandidate)
            }
        }

        val currentStatus = when {
            confirmedThisFrame -> DwellStatus.COMMITTED
            dwellProgress > 0.15f -> DwellStatus.HOLDING
            activeCandidate.isNotBlank() -> DwellStatus.DETECTING
            else -> DwellStatus.IDLE
        }

        return DebounceState(
            candidate = activeCandidate,
            confidence = result.confidence,
            margin = result.margin,
            dwellProgress = dwellProgress,
            confirmed = confirmedThisFrame,
            lastConfirmed = lastCommittedLetter,
            transcript = transcriptBuilder.toString(),
            top3 = result.top3,
            status = currentStatus
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
        lastCommittedLetter = ""
        activeCandidate = ""
        dwellAccumulatedMs = 0L
        committedWaitingRelease = false
        voteWindow.clear()
        return ""
    }

    fun getTranscript(): String = transcriptBuilder.toString()
}
