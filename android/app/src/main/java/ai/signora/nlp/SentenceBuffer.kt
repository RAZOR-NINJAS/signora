package ai.signora.nlp

/**
 * Accumulates committed GlossTokens and fires sentence boundaries.
 *
 * A sentence boundary is triggered when:
 *  1. The gap since the last committed token exceeds [sentencePauseMs] (default 1500ms).
 *  2. An explicit SPACE or PERIOD sign is committed.
 *  3. [flush] is called manually (e.g. by the Speak button).
 *
 * When a boundary fires, [onSentenceComplete] is called with the current token list,
 * and the buffer is cleared.
 */
class SentenceBuffer(
    private val sentencePauseMs: Long = 1800L,
    private val onSentenceComplete: (List<GlossToken>) -> Unit
) {
    private val tokens = mutableListOf<GlossToken>()
    private var lastTokenTimeMs: Long = 0L
    private var isFinalized: Boolean = false

    /** Called by MainActivity every frame tick so boundary detection is time-driven. */
    fun tick(nowMs: Long = System.currentTimeMillis()) {
        if (tokens.isEmpty() || isFinalized) return
        if (lastTokenTimeMs > 0L && (nowMs - lastTokenTimeMs) >= sentencePauseMs) {
            finalizeAndFire()
        }
    }

    /**
     * Add a newly confirmed sign token to the buffer.
     * If previous sentence was finalized, start fresh with this token.
     */
    fun push(token: GlossToken) {
        if (isFinalized) {
            tokens.clear()
            isFinalized = false
        }
        val now = token.timestamp
        lastTokenTimeMs = now

        when (token.label.uppercase()) {
            "PERIOD", "SENTENCE_END", "FULL_STOP" -> {
                tokens.add(token)
                finalizeAndFire()
            }
            else -> tokens.add(token)
        }
    }

    val isFinalizedState: Boolean
        get() = isFinalized

    /** Manually flush – called when user presses Speak or hands rest. */
    fun flush() {
        if (tokens.isNotEmpty() && !isFinalized) finalizeAndFire()
    }

    /** Returns a snapshot of pending tokens without clearing. */
    fun peek(): List<GlossToken> = tokens.toList()

    /** Pop the most recent token (undo / backspace). */
    fun pop(): GlossToken? {
        if (tokens.isNotEmpty()) {
            isFinalized = false
            val tok = tokens.removeAt(tokens.size - 1)
            if (tokens.isEmpty()) {
                lastTokenTimeMs = 0L
            }
            return tok
        }
        return null
    }

    /** Clear without firing the callback – used on transcript Clear. */
    fun clear() {
        tokens.clear()
        lastTokenTimeMs = 0L
        isFinalized = false
    }

    private fun finalizeAndFire() {
        if (tokens.isEmpty()) return
        isFinalized = true
        val snapshot = tokens.toList()
        onSentenceComplete(snapshot)
    }
}
