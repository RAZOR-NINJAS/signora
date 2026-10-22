package ai.signora

import ai.signora.engine.HandPresenceDetector
import org.junit.Assert.*
import org.junit.Test

class HandPresenceDetectorUnitTest {

    @Test
    fun testHysteresisEnterDelay() {
        val detector = HandPresenceDetector(enterDelayMs = 150L, exitDelayMs = 800L)
        var t = 1000L

        // Hand seen for 100ms (< 150ms) -> not yet present
        assertFalse(detector.update(true, t))
        t += 100L
        assertFalse(detector.update(true, t))

        // Hand seen for 160ms (>= 150ms) -> becomes present and collapses
        t += 60L
        assertTrue(detector.update(true, t))
        assertTrue(detector.isHandPresent)
    }

    @Test
    fun testHysteresisExitDelay() {
        val detector = HandPresenceDetector(enterDelayMs = 150L, exitDelayMs = 800L)
        var t = 1000L

        // Establish hand presence
        detector.update(true, t)
        t += 200L
        detector.update(true, t)
        assertTrue(detector.isHandPresent)

        // Hand dropped for 400ms (< 800ms exit delay) -> still present
        t += 400L
        assertTrue(detector.update(false, t))
        assertTrue(detector.isHandPresent)

        // Hand dropped for 850ms (> 800ms) -> exits presence
        t += 450L
        assertFalse(detector.update(false, t))
        assertFalse(detector.isHandPresent)
    }

    @Test
    fun testPinExpandedOverridesHandPresence() {
        val detector = HandPresenceDetector(enterDelayMs = 150L, exitDelayMs = 800L, pinDurationMs = 5000L)
        var t = 1000L

        detector.update(true, t)
        t += 200L
        assertTrue(detector.update(true, t)) // Currently collapsed

        // Pin expanded
        detector.pinExpanded(t)
        assertFalse("Pinned expanded must prevent collapse", detector.shouldCollapse(t))

        // Still present after 2000ms, but pin active for 5000ms
        t += 2000L
        detector.update(true, t)
        assertFalse(detector.shouldCollapse(t))

        // After pin expires (> 5000ms)
        t += 3500L
        detector.update(true, t)
        assertTrue(detector.shouldCollapse(t))
    }

    @Test
    fun testAutoCollapseDisabled() {
        val detector = HandPresenceDetector(autoCollapseEnabled = false)
        var t = 1000L

        detector.update(true, t)
        t += 300L
        detector.update(true, t)
        assertFalse("Disabled auto-collapse should never collapse dock", detector.shouldCollapse(t))
    }

    @Test
    fun testReset() {
        val detector = HandPresenceDetector()
        detector.update(true, 1000L)
        detector.update(true, 1300L)
        assertTrue(detector.isHandPresent)

        detector.reset()
        assertFalse(detector.isHandPresent)
    }
}
