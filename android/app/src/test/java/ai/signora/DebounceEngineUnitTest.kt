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
    fun testHeldLetterDoesNotRepeatAfterAmbiguousFrames() {
        val engine = DebounceEngine(dwellMs = 500L, threshold = 0.65f, minMargin = 0.08f)
        var time = 1000L
        val resA = RecognitionResult("A", 0.95f, 0.50f, listOf("A" to 0.95f, "B" to 0.02f))

        var commits = 0
        for (i in 0 until 10) {
            val s = engine.update(resA, time)
            if (s.confirmed) commits++
            time += 100L
        }
        assertEquals("A must commit exactly once while held", 1, commits)

        // Model still sees a hand and predicts A, but below the gate (ambiguous frame).
        // The sign was never released, so it must not become repeatable.
        val resAmbiguous = RecognitionResult("A", 0.40f, 0.05f, listOf("A" to 0.40f, "S" to 0.35f))
        for (i in 0 until 6) {
            val s = engine.update(resAmbiguous, time)
            assertFalse(s.confirmed)
            time += 100L
        }

        for (i in 0 until 25) {
            val s = engine.update(resA, time)
            if (s.confirmed) commits++
            time += 100L
        }

        assertEquals("A held through an ambiguous drop must not commit twice", 1, commits)
        assertEquals("A", engine.getTranscript())
    }

    @Test
    fun testFlickerToAnotherLetterDoesNotRepeatHeldLetter() {
        val engine = DebounceEngine(dwellMs = 500L, threshold = 0.65f, minMargin = 0.08f)
        var time = 1000L
        val resA = RecognitionResult("A", 0.90f, 0.40f, listOf("A" to 0.90f, "B" to 0.05f))
        val resB = RecognitionResult("B", 0.90f, 0.40f, listOf("B" to 0.90f, "A" to 0.05f))

        var commits = 0
        for (i in 0 until 10) {
            if (engine.update(resA, time).confirmed) commits++
            time += 100L
        }
        assertEquals(1, commits)

        // Misclassification burst long enough to steal the vote window, but far too short
        // to be a deliberate new sign
        for (i in 0 until 5) {
            engine.update(resB, time)
            time += 100L
        }

        for (i in 0 until 25) {
            if (engine.update(resA, time).confirmed) commits++
            time += 100L
        }

        assertEquals("A must not re-commit after a flicker", 1, commits)
        assertEquals("A", engine.getTranscript())
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

    @Test
    fun testHandPresenceEntersAfter150ms() {
        val detector = ai.signora.engine.HandPresenceDetector(enterDelayMs = 150L, exitDelayMs = 800L)
        var time = 1000L

        // Initial state: not collapsed
        assertFalse(detector.update(hasHand = false, nowMs = time))

        // Hand seen at 1000ms: not yet 150ms
        assertFalse(detector.update(hasHand = true, nowMs = time))

        // Hand seen at 1100ms: 100ms elapsed, still < 150ms
        assertFalse(detector.update(hasHand = true, nowMs = time + 100L))

        // Hand seen at 1160ms: 160ms elapsed >= 150ms -> should collapse!
        assertTrue(detector.update(hasHand = true, nowMs = time + 160L))
        assertTrue(detector.isHandPresent)
    }

    @Test
    fun testHandPresenceDropoutUnder800msDoesNotExpand() {
        val detector = ai.signora.engine.HandPresenceDetector(enterDelayMs = 150L, exitDelayMs = 800L)
        var time = 1000L

        // Enter presence
        detector.update(hasHand = true, nowMs = time)
        detector.update(hasHand = true, nowMs = time + 200L)
        assertTrue(detector.isHandPresent)

        // Hand lost for 300ms (< 800ms dropout)
        assertTrue(detector.update(hasHand = false, nowMs = time + 500L))
        assertTrue("Brief dropout under 800ms must not expand", detector.isHandPresent)

        // Hand returns at 600ms
        assertTrue(detector.update(hasHand = true, nowMs = time + 600L))
        assertTrue(detector.isHandPresent)
    }

    @Test
    fun testHandPresenceExitsOnlyAfter800ms() {
        val detector = ai.signora.engine.HandPresenceDetector(enterDelayMs = 150L, exitDelayMs = 800L)
        var time = 1000L

        // Enter presence
        detector.update(hasHand = true, nowMs = time)
        detector.update(hasHand = true, nowMs = time + 200L)
        assertTrue(detector.isHandPresent)

        // Hand absent for 850ms (> 800ms)
        val shouldCollapse = detector.update(hasHand = false, nowMs = time + 200L + 850L)
        assertFalse("Absence > 800ms must exit collapsed state", shouldCollapse)
        assertFalse(detector.isHandPresent)
    }

    @Test
    fun testManualPinOverrideStaysExpandedFor5000ms() {
        val detector = ai.signora.engine.HandPresenceDetector(enterDelayMs = 150L, exitDelayMs = 800L, pinDurationMs = 5000L)
        var time = 1000L

        // Enter presence
        detector.update(hasHand = true, nowMs = time)
        detector.update(hasHand = true, nowMs = time + 200L)
        assertTrue(detector.isHandPresent)
        assertTrue(detector.shouldCollapse(nowMs = time + 200L))

        // User taps collapsed pill to pin expanded
        detector.pinExpanded(nowMs = time + 250L)

        // Even though hand is continuously present, should NOT collapse during pin
        assertFalse("Must stay expanded while pinned", detector.update(hasHand = true, nowMs = time + 1000L))
        assertFalse("Must stay expanded while pinned", detector.update(hasHand = true, nowMs = time + 4000L))

        // After 5000ms passes (nowMs = time + 5300L), pin expires and collapses again
        assertTrue("Must re-collapse after pin duration expires", detector.update(hasHand = true, nowMs = time + 5300L))
    }

    @Test
    fun testMotionGestureDynamicCommitWithoutStaticDwell() {
        val engine = DebounceEngine(dwellMs = 500L, threshold = 0.60f, minMargin = 0.05f)
        var time = 1000L
        val resHelloMotion = RecognitionResult("HELLO_MOTION", 0.85f, 0.40f)

        // Feed 1st and 2nd frame of motion gesture while hand is moving
        val s1 = engine.update(resHelloMotion, time, isMoving = true)
        assertFalse(s1.confirmed)
        time += 50L
        val s2 = engine.update(resHelloMotion, time, isMoving = true)
        assertFalse(s2.confirmed)

        // 3rd frame within 100ms: dynamic motion threshold reached -> commits without requiring 500ms static dwell!
        time += 50L
        val s3 = engine.update(resHelloMotion, time, isMoving = true)
        assertTrue("Motion gesture must commit after dynamic threshold", s3.confirmed)
        assertEquals("HELLO", s3.lastConfirmed)
        assertEquals("HELLO ", engine.getTranscript())
    }

    @Test
    fun testMotionGestureMyNameIsMotionCleansToMyNameIs() {
        val engine = DebounceEngine(dwellMs = 500L, threshold = 0.60f, minMargin = 0.05f)
        var time = 2000L
        val resNameMotion = RecognitionResult("MY_NAME_IS_MOTION", 0.88f, 0.50f)

        // Multi-stage gesture: the opening frames must not commit on the motion fast path
        for (i in 0 until 3) {
            val partial = engine.update(resNameMotion, time, isMoving = true)
            assertFalse("MY_NAME_IS must not commit on a partial gesture", partial.confirmed)
            time += 100L
        }

        // Hold follows the speaker's dwell profile rather than a hardcoded value
        var commitState = engine.update(resNameMotion, time, isMoving = true)
        while (!commitState.confirmed && time < 6000L) {
            time += 100L
            commitState = engine.update(resNameMotion, time, isMoving = true)
        }

        assertEquals("MY_NAME_IS must follow the dwell profile", 500L, commitState.effectiveDwellMs)
        assertTrue(commitState.confirmed)
        assertEquals("MY_NAME_IS", commitState.lastConfirmed)
        assertEquals("MY_NAME_IS ", engine.getTranscript())
        assertEquals("My name is", DebounceEngine.formatDisplayText(commitState.lastConfirmed))
        assertEquals("My name is", DebounceEngine.formatTranscriptForDisplay(engine.getTranscript()))
    }

    @Test
    fun testFormatDisplayTextMappings() {
        assertEquals("My name is", DebounceEngine.formatDisplayText("MY_NAME_IS"))
        assertEquals("My name is", DebounceEngine.formatDisplayText("MY_NAME_IS_MOTION"))
        assertEquals("Thank you", DebounceEngine.formatDisplayText("THANK_YOU"))
        assertEquals("Hello", DebounceEngine.formatDisplayText("HELLO"))
        assertEquals("Hello", DebounceEngine.formatDisplayText("HELLO_MOTION"))
        assertEquals("Namaste", DebounceEngine.formatDisplayText("NAMASTE"))
        assertEquals("Help", DebounceEngine.formatDisplayText("HELP"))
        assertEquals("Please", DebounceEngine.formatDisplayText("PLEASE"))
        assertEquals("Congratulations", DebounceEngine.formatDisplayText("CONGRATS"))
        assertEquals("Signora", DebounceEngine.formatDisplayText("SIGNORA"))
        assertEquals("Yes", DebounceEngine.formatDisplayText("YES"))
        assertEquals("No", DebounceEngine.formatDisplayText("NO"))
        assertEquals("A", DebounceEngine.formatDisplayText("A"))

        val multiTranscript = "HELLO MY_NAME_IS THANK_YOU "
        assertEquals("Hello My name is Thank you", DebounceEngine.formatTranscriptForDisplay(multiTranscript))
    }

    @Test
    fun testStaticSignRejectsTooFastCommit() {
        // Detection is too fast when dwell is insufficient; verify static sign requires full dwell
        val engine = DebounceEngine(dwellMs = 500L, threshold = 0.60f, minMargin = 0.05f)
        var time = 1000L
        val resA = RecognitionResult("A", 0.90f, 0.40f)

        // 200ms of holding (2 frames): must NOT commit
        engine.update(resA, time, isMoving = false)
        time += 100L
        val fastState = engine.update(resA, time, isMoving = false)
        assertFalse("Static sign must not commit in 100ms", fastState.confirmed)

        // Hold until >= 500ms dwell accumulated
        var committed = false
        var confirmedState: ai.signora.models.DebounceState? = null
        for (i in 0 until 6) {
            time += 100L
            val state = engine.update(resA, time, isMoving = false)
            if (state.confirmed) {
                committed = true
                confirmedState = state
                break
            }
        }
        assertTrue("Static sign must commit after steady dwell", committed)
        assertEquals("A", confirmedState?.lastConfirmed)
    }

    @Test
    fun testMotionCommitDeadlockReleasedOnCandidateChange() {
        val engine = DebounceEngine(dwellMs = 350L, threshold = 0.50f, minMargin = 0.05f)
        var time = 1000L
        val resMotion = RecognitionResult("HELLO_MOTION", 0.90f, 0.40f)

        // 3 frames commit motion gesture
        engine.update(resMotion, time, isMoving = true)
        time += 50L
        engine.update(resMotion, time, isMoving = true)
        time += 50L
        val motionState = engine.update(resMotion, time, isMoving = true)
        assertTrue("HELLO_MOTION must commit", motionState.confirmed)
        assertEquals("HELLO", motionState.lastConfirmed)

        // User now transitions to a static sign "NAMASTE" without withdrawing hand
        time += 100L
        val resNamaste = RecognitionResult("NAMASTE", 0.90f, 0.40f)
        var namasteCommitted = false

        for (i in 0 until 8) {
            time += 100L
            val state = engine.update(resNamaste, time, isMoving = false)
            if (state.confirmed) {
                namasteCommitted = true
                assertEquals("NAMASTE", state.lastConfirmed)
                break
            }
        }

        assertTrue("Static sign NAMASTE must commit after HELLO_MOTION without needing hand withdrawal", namasteCommitted)
    }

    @Test
    fun testSingleFrameFlickerToleratedWithoutResettingDwell() {
        val engine = DebounceEngine(dwellMs = 500L, threshold = 0.50f, minMargin = 0.05f)
        var time = 1000L
        val resC = RecognitionResult("C", 0.90f, 0.40f)
        val resO = RecognitionResult("O", 0.90f, 0.40f)

        // Hold C for 300ms
        for (i in 0 until 3) {
            engine.update(resC, time)
            time += 100L
        }
        val progressBeforeFlicker = engine.update(resC, time).dwellProgress
        assertTrue("Dwell should have accumulated ~300ms", progressBeforeFlicker >= 0.5f)

        // 1 spurious frame flickers to O
        time += 100L
        val flickerState = engine.update(resO, time)
        // Candidate should still be C and dwell progress should not be wiped to zero
        assertEquals("C", flickerState.candidate)
        assertTrue("Dwell progress must not be wiped to zero on 1 flicker frame", flickerState.dwellProgress >= 0.45f)

        // Returns to C on next frame
        time += 100L
        val resumedState = engine.update(resC, time)
        assertEquals("C", resumedState.candidate)
        assertTrue("Dwell progress should resume accumulating", resumedState.dwellProgress > progressBeforeFlicker)
    }
    @Test
    fun testCustomMotionSignRequiresFullDwell() {
        val engine = DebounceEngine(dwellMs = 1000L, threshold = 0.65f, minMargin = 0.08f)
        var time = 1000L
        val res = RecognitionResult(
            "SUII_MOTION", 0.95f, 0.60f,
            listOf("SUII_MOTION" to 0.95f, "HELLO_MOTION" to 0.03f)
        )

        // The 3-frame motion fast path must not apply to user-trained motion signs
        for (i in 0 until 3) {
            val state = engine.update(res, time, isMoving = true)
            assertFalse("SUII must not commit within 3 frames", state.confirmed)
            time += 100L
        }

        // SUII holds deliberately longer than the 1s default dwell
        var lastState = engine.update(res, time, isMoving = true)
        time += 100L
        while (time <= 3900L) {
            lastState = engine.update(res, time, isMoving = true)
            assertFalse("SUII must not commit before 3s of hold (t=${time}ms)", lastState.confirmed)
            time += 100L
        }
        assertEquals("SUII effective dwell must be 3s", 3000L, lastState.effectiveDwellMs)

        val commitState = engine.update(res, 4000L, isMoving = true)
        assertTrue("SUII must commit once the 3s hold is reached", commitState.confirmed)
        assertEquals("SUII", commitState.lastConfirmed)
    }

    @Test
    fun testConfusablePairRequiresClearMargin() {
        val engine = DebounceEngine(dwellMs = 1000L, threshold = 0.50f, minMargin = 0.08f)
        var time = 1000L
        val ambiguous = RecognitionResult(
            "I", 0.60f, 0.10f,
            listOf("I" to 0.60f, "Y" to 0.50f)
        )
        var confirmed = false
        for (i in 0 until 30) {
            if (engine.update(ambiguous, time).confirmed) confirmed = true
            time += 100L
        }
        assertFalse("Ambiguous I/Y must not be guessed", confirmed)

        val clearEngine = DebounceEngine(dwellMs = 1000L, threshold = 0.50f, minMargin = 0.08f)
        var clearTime = 1000L
        val clear = RecognitionResult(
            "I", 0.85f, 0.40f,
            listOf("I" to 0.85f, "Y" to 0.45f)
        )
        var clearCommitted = false
        for (i in 0 until 25) {
            if (clearEngine.update(clear, clearTime).confirmed) {
                clearCommitted = true
                break
            }
            clearTime += 100L
        }
        assertTrue("Clear I should still commit", clearCommitted)
    }
}
