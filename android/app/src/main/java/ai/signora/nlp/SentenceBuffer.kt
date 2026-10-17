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
    private val sentencePauseMs: Long = 1500L,
    private val onSentenceComplete: (List<GlossToken>) -> Unit
) {
    private val tokens = mutableListOf<GlossToken>()
    private var lastTokenTimeMs: Long = 0L

    /** Called by MainActivity every frame tick so boundary detection is time-driven. */
    fun tick(nowMs: Long = System.currentTimeMillis()) {
        if (tokens.isEmpty()) return
        if (lastTokenTimeMs > 0L && (nowMs - lastTokenTimeMs) >= sentencePauseMs) {
            fireAndClear()
        }
    }

    /**
     * Add a newly confirmed sign token to the buffer.
     * If the label is a terminal marker (PERIOD, SENTENCE_END) or SPACE at end of a word
     * cluster, fire immediately.
     */
    fun push(token: GlossToken) {
        val now = token.timestamp
        lastTokenTimeMs = now

        when (token.label.uppercase()) {
            "PERIOD", "SENTENCE_END", "FULL_STOP" -> {
                tokens.add(token)
                fireAndClear()
            }
            // SPACE just advances word boundary but doesn't end a sentence on its own;
            // the pause timer handles that.
            else -> tokens.add(token)
        }
    }

    /** Manually flush – called when user presses Speak. */
    fun flush() {
        if (tokens.isNotEmpty()) fireAndClear()
    }

    /** Returns a snapshot of pending tokens without clearing. */
    fun peek(): List<GlossToken> = tokens.toList()

    /** Clear without firing the callback – used on transcript Clear. */
    fun clear() {
        tokens.clear()
        lastTokenTimeMs = 0L
    }

    private fun fireAndClear() {
        val snapshot = tokens.toList()
        tokens.clear()
        lastTokenTimeMs = 0L
        if (snapshot.isNotEmpty()) {
            onSentenceComplete(snapshot)
        }
    }
}
