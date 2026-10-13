package ai.signora.engine

/**
 * State machine managing hand presence hysteresis for smooth, flicker-free
 * bottom bar collapse and expansion.
 *
 * Requirements:
 * - Enter collapsed after hand detected continuously for ~150 ms.
 * - Return to expanded only after NO hand for ~800 ms (dropout tolerance).
 * - Manual pin override: User tapping collapsed pill pins expanded for 5000 ms.
 * - Auto-collapse enabled setting (default ON).
 */
class HandPresenceDetector(
    var enterDelayMs: Long = 150L,
    var exitDelayMs: Long = 800L,
    var pinDurationMs: Long = 5000L,
    var autoCollapseEnabled: Boolean = true
) {
    var isHandPresent: Boolean = false
        private set

    private var firstHandDetectedMs: Long = 0L
    private var lastHandSeenMs: Long = 0L
    private var pinUntilMs: Long = 0L

    /**
     * Updates hand presence state based on whether at least one valid hand is seen.
     * Returns true if the bottom bar should be collapsed.
     */
    fun update(hasHand: Boolean, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (hasHand) {
            // Hand is seen
            lastHandSeenMs = nowMs
            if (firstHandDetectedMs == 0L) {
                firstHandDetectedMs = nowMs
            }

            // Enter collapsed after continuous presence exceeds enterDelayMs
            if (!isHandPresent && (nowMs - firstHandDetectedMs >= enterDelayMs)) {
                isHandPresent = true
            }
        } else {
            // No hand seen
            firstHandDetectedMs = 0L

            // Exit collapsed only after absence exceeds exitDelayMs
            if (isHandPresent && (lastHandSeenMs > 0L && (nowMs - lastHandSeenMs >= exitDelayMs))) {
                isHandPresent = false
            }
        }

        return shouldCollapse(nowMs)
    }

    /**
     * Pins the UI expanded for 5 seconds (or pinDurationMs).
     */
    fun pinExpanded(nowMs: Long = System.currentTimeMillis()) {
        pinUntilMs = nowMs + pinDurationMs
    }

    /**
     * Manually unpins to allow immediate collapse if hand is present.
     */
    fun unpin() {
        pinUntilMs = 0L
    }

    /**
     * Determines whether the dock should be visually collapsed.
     */
    fun shouldCollapse(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!autoCollapseEnabled) return false
        if (nowMs < pinUntilMs) return false // Pinned expanded
        return isHandPresent
    }

    fun reset() {
        isHandPresent = false
        firstHandDetectedMs = 0L
        lastHandSeenMs = 0L
        pinUntilMs = 0L
    }
}
