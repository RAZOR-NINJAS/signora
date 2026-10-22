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
    var threshold: Float = 0.65f,
    var minMargin: Float = 0.08f,
    private val voteWindowSize: Int = 7,
    private val maxDropoutMs: Long = 150L,
    private val spaceDwellMs: Long = 800L,
    var fluidMode: Boolean = true
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

    // Label flicker tolerance
    private var pendingCandidate: String = ""
    private var candidateMismatchCount: Int = 0

    // Motion gesture accumulator: tracks dynamic motion gesture hits
    private val motionHistory = ArrayDeque<Pair<Long, String>>()
    private var lastMotionCommitTimeMs: Long = -10000L

    // Kinetic tracking for posture apex acceleration
    private var lastWristX: Float? = null
    private var lastWristY: Float? = null
    private var lastWristTimeMs: Long = 0L
    private var handVelocity: Float = 0f

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
        setOf("K", "P"),
        setOf("I", "Y")
    )

    fun isMotionSign(label: String): Boolean = Companion.isMotionSign(label)

    /** Dwell required for a candidate, honouring per-sign overrides (e.g. SUII). */
    private fun requiredDwellFor(label: String): Long {
        if (label == "SPACE") return spaceDwellMs
        return DWELL_OVERRIDES[label.uppercase().trim()] ?: dwellMs
    }

    private fun hasDwellOverride(label: String): Boolean =
        DWELL_OVERRIDES.containsKey(label.uppercase().trim())

    fun update(
        result: RecognitionResult?,
        nowMs: Long = System.currentTimeMillis(),
        landmarks: List<ai.signora.models.Point3D>? = null,
        isMoving: Boolean = false
    ): DebounceState {
        // 0. Update kinetic velocity tracking
        if (landmarks != null && landmarks.isNotEmpty()) {
            val wrist = landmarks[0]
            if (lastWristX != null && lastWristY != null && lastWristTimeMs > 0L) {
                val dtS = ((nowMs - lastWristTimeMs).coerceAtLeast(5L)).toFloat() / 1000f
                val dx = wrist.x - lastWristX!!
                val dy = wrist.y - lastWristY!!
                val instV = kotlin.math.sqrt(dx * dx + dy * dy) / dtS
                handVelocity = 0.65f * handVelocity + 0.35f * instV
            }
            lastWristX = wrist.x
            lastWristY = wrist.y
            lastWristTimeMs = nowMs
        }

        // 1. Handle Hand Presence / Loss
        val isMotion = result != null && isMotionSign(result.label)
        val isSlowMotion = result != null && SLOW_MOTION_SIGNS.contains(result.label.uppercase().trim())
        val effThreshold = if (isMotion && !isSlowMotion) 0.40f else threshold
        val top2 = result?.top3?.take(2)?.map { it.first.uppercase().trim() }?.distinct().orEmpty()
        val confusableTop2 = top2.size == 2 && confusablePairs.any { it.contains(top2[0]) && it.contains(top2[1]) }
        val effMargin = when {
            isMotion && !isSlowMotion -> 0.03f
            // Ambiguous pair: demand a clear winner so the engine does not guess
            confusableTop2 -> maxOf(minMargin, 0.15f)
            else -> minMargin
        }
        if (result == null || result.confidence < effThreshold || result.margin < effMargin) {
            val timeSinceLastHand = if (lastHandSeenTimeMs > 0L) nowMs - lastHandSeenTimeMs else Long.MAX_VALUE

            if (timeSinceLastHand > maxDropoutMs) {
                // Extended loss: reset dwell progress and clear candidate
                activeCandidate = ""
                dwellAccumulatedMs = 0L
                // A genuine hand-away (no prediction at all) is a deliberate release, so the
                // repeat lock clears. A run of low-confidence or ambiguous frames is not a
                // release: erasing the committed sign there lets a letter still being held
                // commit a second time.
                if (result == null) {
                    committedWaitingRelease = false
                    lastCommittedLetter = ""
                }
                voteWindow.clear()
                motionHistory.clear()
                pendingCandidate = ""
                candidateMismatchCount = 0
                lastWristX = null
                lastWristY = null
                lastWristTimeMs = 0L
                handVelocity = 0f
            }
            // If within dropout tolerance, dwellAccumulatedMs is paused (neither increases nor resets)

            lastFrameTimeMs = nowMs

            val req = requiredDwellFor(activeCandidate)
            return DebounceState(
                candidate = activeCandidate,
                confidence = 0f,
                margin = 0f,
                dwellProgress = if (activeCandidate.isNotBlank()) {
                    (dwellAccumulatedMs.toFloat() / req.toFloat()).coerceIn(0f, 1f)
                } else 0f,
                confirmed = false,
                lastConfirmed = lastCommittedLetter,
                transcript = transcriptBuilder.toString(),
                status = if (timeSinceLastHand > maxDropoutMs) DwellStatus.NO_HAND else DwellStatus.HOLDING,
                isApex = false,
                effectiveDwellMs = req
            )
        }

        // 2. Hand is actively detected and passes confidence & margin thresholds
        lastHandSeenTimeMs = nowMs
        val rawLabel = result.label

        // 2.1 Dynamic motion gesture pipeline
        val isHandMoving = (isMoving && handVelocity >= 0.15f) || handVelocity >= 0.22f
        if (isMotion && (isHandMoving || isMoving) && result.confidence >= 0.50f) {
            motionHistory.addLast(nowMs to rawLabel)
        }

        // Prune motion history older than 850ms
        while (motionHistory.isNotEmpty() && (nowMs - motionHistory.first().first) > 850L) {
            motionHistory.removeFirst()
        }

        // Check if motion gesture has accumulated sufficient hits
        val cooldownMs = if (cleanLabel(rawLabel) == lastCommittedLetter) 1200L else 750L
        if (isMotion && !isSlowMotion && (lastMotionCommitTimeMs < 0L || (nowMs - lastMotionCommitTimeMs) > cooldownMs)) {
            val motionCounts = mutableMapOf<String, Int>()
            for ((_, lbl) in motionHistory) {
                motionCounts[lbl] = (motionCounts[lbl] ?: 0) + 1
            }
            var topMotion = ""
            var topHits = 0
            for ((lbl, count) in motionCounts) {
                if (count > topHits) {
                    topHits = count
                    topMotion = lbl
                }
            }

            val requiredHits = if (result.confidence >= 0.85f) 3 else if (result.confidence >= 0.70f) 5 else 6
            if (topHits >= requiredHits) {
                val cleaned = cleanLabel(topMotion)
                lastCommittedLetter = cleaned
                lastMotionCommitTimeMs = nowMs
                motionHistory.clear()
                voteWindow.clear()
                activeCandidate = cleaned
                dwellAccumulatedMs = 0L
                committedWaitingRelease = true
                pendingCandidate = ""
                candidateMismatchCount = 0

                if (transcriptBuilder.isNotEmpty() && !transcriptBuilder.endsWith(" ")) {
                    transcriptBuilder.append(" ")
                }
                transcriptBuilder.append(cleaned).append(" ")

                return DebounceState(
                    candidate = cleaned,
                    confidence = result.confidence,
                    margin = result.margin,
                    dwellProgress = 1.0f,
                    confirmed = true,
                    lastConfirmed = cleaned,
                    transcript = transcriptBuilder.toString(),
                    top3 = result.top3,
                    status = DwellStatus.COMMITTED,
                    isApex = true,
                    effectiveDwellMs = 400L
                )
            }
        }

        // 3. Majority Voting with Hysteresis for Static Signs
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

        // The repeat lock is deliberately NOT released on a momentary label change: a single
        // frame of a different dominant label would unlock the committed sign while it is
        // still being held, and its dwell is already at 1.0, so it re-commits instantly.
        // Releases happen when the hand leaves (see the dropout path) or when the candidate
        // genuinely switches to another sign below.

        if (dominantLabel != activeCandidate) {
            if (activeCandidate.isBlank()) {
                if (!isHandMoving || isMotion) {
                    activeCandidate = dominantLabel
                    dwellAccumulatedMs = 0L
                    committedWaitingRelease = (cleanLabel(dominantLabel) == lastCommittedLetter)
                    candidateMismatchCount = 0
                    pendingCandidate = ""
                }
            } else if (dominantLabel == pendingCandidate) {
                candidateMismatchCount++
                val reqMismatchFrames = if (isHandMoving) 4 else 2
                if (candidateMismatchCount >= reqMismatchFrames) {
                    // Confirmed new sign candidate after sustained stable frames
                    activeCandidate = dominantLabel
                    dwellAccumulatedMs = 0L
                    committedWaitingRelease = (cleanLabel(dominantLabel) == lastCommittedLetter)
                    candidateMismatchCount = 0
                    pendingCandidate = ""
                }
            } else {
                // First frame of a candidate mismatch: tolerate as brief flicker and pause dwell
                pendingCandidate = dominantLabel
                candidateMismatchCount = 1
            }
        } else {
            candidateMismatchCount = 0
            pendingCandidate = ""

            if (!committedWaitingRelease) {
                if (!isMotion && isHandMoving) {
                    // Hand is moving in transition between signs: decay uncommitted dwell accumulation
                    dwellAccumulatedMs = (dwellAccumulatedMs - dt * 2).coerceAtLeast(0L)
                } else {
                    dwellAccumulatedMs += dt
                }
            }
        }

        val requiredDwell = requiredDwellFor(activeCandidate)
        var effectiveDwell = requiredDwell.toFloat()
        var isApex = false

        // Signs with an explicit dwell override must hold the exact duration;
        // the fluid/apex shortcuts would shave time off them.
        if (fluidMode && lastWristX != null && !hasDwellOverride(activeCandidate)) {
            if (handVelocity < 0.12f && result.confidence >= 0.88f) {
                // Hand has firmly decelerated into target gesture apex
                effectiveDwell = kotlin.math.max(450f, requiredDwell * 0.85f)
                isApex = true
            } else if (result.confidence >= 0.95f && handVelocity < 0.18f) {
                effectiveDwell = kotlin.math.max(500f, requiredDwell * 0.90f)
            }
        }

        val dwellProgress = (dwellAccumulatedMs.toFloat() / effectiveDwell).coerceIn(0f, 1f)

        // 5. Commit Condition
        if (dwellProgress >= 1.0f && !committedWaitingRelease) {
            confirmedThisFrame = true
            val cleaned = cleanLabel(activeCandidate)
            lastCommittedLetter = cleaned
            committedWaitingRelease = true // Lock until hand released or different sign made

            if (cleaned == "SPACE") {
                if (transcriptBuilder.isNotEmpty() && !transcriptBuilder.endsWith(" ")) {
                    transcriptBuilder.append(" ")
                }
            } else if (cleaned.length == 1) {
                transcriptBuilder.append(cleaned)
            } else {
                // Multi-letter ISL word gloss: ensure space separation
                if (transcriptBuilder.isNotEmpty() && !transcriptBuilder.endsWith(" ")) {
                    transcriptBuilder.append(" ")
                }
                transcriptBuilder.append(cleaned).append(" ")
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
            status = currentStatus,
            isApex = isApex,
            effectiveDwellMs = effectiveDwell.toLong()
        )
    }

    fun backspace(): String {
        if (transcriptBuilder.isNotEmpty()) {
            if (transcriptBuilder.endsWith(" ")) {
                transcriptBuilder.deleteCharAt(transcriptBuilder.length - 1)
            }
            val lastSpace = transcriptBuilder.lastIndexOf(" ")
            val lastWord = if (lastSpace >= 0) transcriptBuilder.substring(lastSpace + 1) else transcriptBuilder.toString()
            if (lastWord.length > 1) {
                if (lastSpace >= 0) {
                    transcriptBuilder.delete(lastSpace, transcriptBuilder.length)
                } else {
                    transcriptBuilder.setLength(0)
                }
            } else if (transcriptBuilder.isNotEmpty()) {
                transcriptBuilder.deleteCharAt(transcriptBuilder.length - 1)
            }
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
        motionHistory.clear()
        pendingCandidate = ""
        candidateMismatchCount = 0
        lastMotionCommitTimeMs = -10000L
        lastWristX = null
        lastWristY = null
        lastWristTimeMs = 0L
        handVelocity = 0f
        return ""
    }

    fun getTranscript(): String = transcriptBuilder.toString()

    companion object {
        val MOTION_SIGNS = setOf(
            "HELLO_MOTION", "YOU_MOTION", "MY_NAME_IS_MOTION",
            "NO_MOTION", "WHAT_MOTION", "J_MOTION", "Z_MOTION"
        )

        // Motion signs that must hold a dwell instead of the 3-frame fast commit:
        // user-trained signs (no built-in trajectory backs them up) and multi-stage
        // signs like MY_NAME_IS, whose opening phase already reads as the full sign.
        // MY_NAME_IS deliberately has no override, so its hold follows the dwell profile.
        val SLOW_MOTION_SIGNS = setOf("SUII_MOTION", "HALA_MADRID_MOTION", "MY_NAME_IS_MOTION")

        // Per-sign dwell requirements, in milliseconds. Signs left out of this table
        // follow the user's dwell profile (FLUID 500 / STANDARD 1000 / STEADY 1500).
        val DWELL_OVERRIDES = mapOf(
            "SUII_MOTION" to 3000L,
            "SUII" to 3000L
        )

        fun isMotionSign(label: String): Boolean {
            val u = label.uppercase().trim()
            return u.endsWith("_MOTION") || MOTION_SIGNS.contains(u)
        }

        fun cleanLabel(label: String): String {
            val u = label.uppercase().trim()
            return when (u) {
                "MY_NAME_IS_MOTION" -> "MY_NAME_IS"
                "HELLO_MOTION" -> "HELLO"
                "YOU_MOTION" -> "YOU"
                "NO_MOTION" -> "NO"
                "WHAT_MOTION" -> "WHAT"
                "J_MOTION" -> "J"
                "Z_MOTION" -> "Z"
                else -> if (u.endsWith("_MOTION")) u.removeSuffix("_MOTION") else label
            }
        }

        fun formatDisplayText(label: String): String {
            val u = cleanLabel(label)
            return when (u.uppercase()) {
                "MY_NAME_IS" -> "My name is"
                "THANK_YOU" -> "Thank you"
                "HELLO" -> "Hello"
                "NAMASTE" -> "Namaste"
                "HELP" -> "Help"
                "PLEASE" -> "Please"
                "CONGRATS" -> "Congratulations"
                "SIGNORA" -> "Signora"
                "YES" -> "Yes"
                "NO" -> "No"
                "YOU" -> "You"
                "WHAT" -> "What"
                "MEET" -> "Meet"
                "SPACE" -> " "
                else -> {
                    if (u.contains("_")) {
                        u.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    } else if (u.length > 1) {
                        u.lowercase().replaceFirstChar { c -> c.uppercase() }
                    } else {
                        u
                    }
                }
            }
        }

        fun formatTranscriptForDisplay(rawTranscript: String): String {
            if (rawTranscript.isBlank()) return ""
            val tokens = rawTranscript.trim().split("\\s+".toRegex())
            return tokens.joinToString(" ") { tok -> formatDisplayText(tok) }
        }
    }
}
