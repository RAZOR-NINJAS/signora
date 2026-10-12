package ai.signora

import ai.signora.engine.DebounceEngine
import ai.signora.models.DwellStatus
import ai.signora.models.RecognitionResult
import org.junit.Assert.*
import org.junit.Test

class DebounceEngineUnitTest {

    @Test
    fun testStableLetterCommit() {
        val engine = DebounceEngine(dwellMs = 1000L, threshold = 0.50f, minMargin = 0.10f)
        var time = 1000L
        val res = RecognitionResult("A", 0.90f, 0.40f)

        // Feed frames for 800ms
        for (i in 0 until 8) {
            val state = engine.update(res, time)
            assertFalse("Letter should not commit before dwellMs", state.confirmed)
            assertEquals("A", state.candidate)
            time += 100L
        }

        // Feed until >= 1000ms dwell accumulated
        var committed = false
        for (i in 0 until 4) {
            val state = engine.update(res, time)
            if (state.confirmed) {
                committed = true
                assertEquals("A", state.lastConfirmed)
                assertEquals("A", state.transcript)
                assertEquals(DwellStatus.COMMITTED, state.status)
                break
            }
            time += 100L
        }

        assertTrue("Letter must commit at >= 1000ms dwell", committed)
    }

    @Test
    fun testLetterChangeResetsDwell() {
        val engine = DebounceEngine(dwellMs = 1000L, threshold = 0.50f, minMargin = 0.10f)
        var time = 1000L
        val resA = RecognitionResult("A", 0.90f, 0.40f)

        // Hold A for 600ms
        for (i in 0 until 6) {
            engine.update(resA, time)
            time += 100L
        }

        // Feed B enough times to establish majority in 7-frame window
        val resB = RecognitionResult("B", 0.90f, 0.40f)
        var stateB = engine.update(resB, time)
        for (i in 0 until 5) {
            time += 100L
            stateB = engine.update(resB, time)
        }

        assertEquals("Candidate should update to B after majority vote", "B", stateB.candidate)
        assertFalse("B should not be confirmed immediately", stateB.confirmed)
        assertTrue("Dwell progress must be well below 1.0", stateB.dwellProgress < 0.6f)
    }

    @Test
    fun testDropoutTolerancePauseVsReset() {
        val engine = DebounceEngine(dwellMs = 1000L, threshold = 0.50f, minMargin = 0.10f, maxDropoutMs = 150L)
        var time = 1000L
        val resA = RecognitionResult("A", 0.90f, 0.40f)

        // Hold A for 500ms
        for (i in 0 until 5) {
            engine.update(resA, time)
            time += 100L
        }
        val progressBeforeDropout = engine.update(resA, time).dwellProgress
        assertTrue("Progress should be around 0.5", progressBeforeDropout >= 0.45f)

        // Brief dropout of 80ms (<= 150ms)
        time += 80L
        val pauseState = engine.update(null, time)
        assertEquals("Candidate must be preserved during brief dropout", "A", pauseState.candidate)
        assertEquals("Dwell progress must be paused during brief dropout", progressBeforeDropout, pauseState.dwellProgress, 0.05f)

        // Extended dropout of 200ms (> 150ms)
        time += 200L
        val resetState = engine.update(null, time)
        assertEquals("Candidate should clear after extended dropout", "", resetState.candidate)
        assertEquals("Dwell progress should reset to 0 after extended dropout", 0f, resetState.dwellProgress, 0.01f)
        assertEquals(DwellStatus.NO_HAND, resetState.status)
    }

    @Test
    fun testDeliberateDoubleLetterRequiresRelease() {
        val engine = DebounceEngine(dwellMs = 500L, threshold = 0.50f, minMargin = 0.10f)
        var time = 1000L
        val resL = RecognitionResult("L", 0.95f, 0.50f)

        // Hold L until committed
        var committedCount = 0
        for (i in 0 until 12) {
            val state = engine.update(resL, time)
            if (state.confirmed) committedCount++
            time += 100L
        }

        // Even though L was held continuously for 1200ms, it should only commit ONCE!
        assertEquals("Letter must only commit once without release", 1, committedCount)
        assertEquals("Transcript must have single 'L'", "L", engine.getTranscript())

        // Now briefly release hand (200ms dropout > 150ms)
        time += 200L
        engine.update(null, time)

        // Hold L again
        time += 50L
        for (i in 0 until 8) {
            val state = engine.update(resL, time)
            if (state.confirmed) committedCount++
            time += 100L
        }

        assertEquals("Second 'L' must commit after release", 2, committedCount)
        assertEquals("Transcript must have 'LL'", "LL", engine.getTranscript())
    }

    @Test
    fun testMarginGatingRejectsAmbiguousPredictions() {
        val engine = DebounceEngine(dwellMs = 1000L, threshold = 0.50f, minMargin = 0.15f)
        val time = 1000L

        // High confidence (0.80) but low margin (0.05) between top1 and top2
        val ambiguousResult = RecognitionResult("U", 0.80f, margin = 0.05f)
        val state = engine.update(ambiguousResult, time)

        assertEquals("Ambiguous predictions with low margin must be rejected", "", state.candidate)
        assertEquals(0f, state.dwellProgress, 0.01f)
    }
}
