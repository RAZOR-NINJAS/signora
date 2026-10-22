package ai.signora.nlp

/**
 * A single committed sign token with metadata captured at commit time.
 *
 * @param label       The confirmed sign label (e.g. "HELLO", "DOCTOR", "A").
 * @param bodyAnchor  Body segment ID where the hand was anchored (e.g. "CHEST_LEFT", "HEAD").
 * @param timestamp   Epoch millis when this token was committed.
 * @param confidence  Model confidence at time of commit.
 */
data class GlossToken(
    val label: String,
    val bodyAnchor: String = "",
    val timestamp: Long = 0L,
    val confidence: Float = 1.0f
)
