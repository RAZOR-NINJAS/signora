package ai.signora

import ai.signora.engine.HandMotionTracker
import ai.signora.engine.PATTERN_J_TRACE
import ai.signora.engine.PATTERN_STATIC
import ai.signora.engine.PATTERN_SWIPE_RIGHT
import ai.signora.engine.PATTERN_WAVE
import ai.signora.engine.PATTERN_Z_TRACE
import ai.signora.models.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandMotionTrackerUnitTest {

    private fun createMockHand(
        wrist: Point3D = Point3D(0.5f, 0.5f, 0f),
        indexTip: Point3D = Point3D(0.5f, 0.4f, 0f),
        pinkyTip: Point3D = Point3D(0.5f, 0.45f, 0f),
        isOpen: Boolean = false,
        isIPosture: Boolean = false,
        isIndexOnly: Boolean = false
    ): List<Point3D> {
        val pts = MutableList(21) { Point3D(wrist.x, wrist.y, wrist.z) }
        pts[0] = wrist
        pts[9] = Point3D(wrist.x, wrist.y - 0.10f, wrist.z) // middle MCP
        pts[5] = Point3D(wrist.x - 0.03f, wrist.y - 0.09f, wrist.z) // index MCP
        pts[13] = Point3D(wrist.x + 0.01f, wrist.y - 0.09f, wrist.z) // ring MCP
        pts[17] = Point3D(wrist.x + 0.03f, wrist.y - 0.08f, wrist.z) // pinky MCP
        pts[2] = Point3D(wrist.x - 0.04f, wrist.y - 0.04f, wrist.z) // thumb MCP
        pts[4] = Point3D(wrist.x - 0.08f, wrist.y - 0.06f, wrist.z) // thumb tip

        if (isOpen) {
            pts[8] = Point3D(wrist.x - 0.03f, wrist.y - 0.18f, wrist.z)
            pts[12] = Point3D(wrist.x, wrist.y - 0.19f, wrist.z)
            pts[16] = Point3D(wrist.x + 0.02f, wrist.y - 0.18f, wrist.z)
            pts[20] = Point3D(wrist.x + 0.04f, wrist.y - 0.17f, wrist.z)
        } else if (isIPosture) {
            pts[8] = Point3D(wrist.x - 0.03f, wrist.y - 0.08f, wrist.z)
            pts[12] = Point3D(wrist.x, wrist.y - 0.08f, wrist.z)
            pts[16] = Point3D(wrist.x + 0.02f, wrist.y - 0.08f, wrist.z)
            pts[20] = pinkyTip
        } else if (isIndexOnly) {
            pts[8] = indexTip
            pts[12] = Point3D(wrist.x, wrist.y - 0.08f, wrist.z)
            pts[16] = Point3D(wrist.x + 0.02f, wrist.y - 0.08f, wrist.z)
            pts[20] = Point3D(wrist.x + 0.04f, wrist.y - 0.07f, wrist.z)
        }
        return pts
    }

    @Test
    fun testStaticHandMovement() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        for (i in 0 until 10) {
            tracker.update(
                rightWrist = Pair(0.5f, 0.5f),
                leftWrist = null,
                timestampMs = t0 + i * 33L
            )
        }
        val state = tracker.latestState
        assertEquals(PATTERN_STATIC, state.pattern)
        assertTrue(state.speed < 0.08f)
    }

    @Test
    fun testSwipeRightMovement() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        for (i in 0 until 12) {
            tracker.update(
                rightWrist = Pair(0.2f + i * 0.035f, 0.5f),
                leftWrist = null,
                timestampMs = t0 + i * 33L
            )
        }
        val state = tracker.latestState
        assertEquals(PATTERN_SWIPE_RIGHT, state.pattern)
        assertTrue(state.isMoving)
    }

    @Test
    fun testWaveOscillation() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        for (i in 0 until 24) {
            val x = 0.5f + 0.08f * kotlin.math.sin(i * 0.7f)
            tracker.update(
                rightWrist = Pair(x, 0.5f),
                leftWrist = null,
                timestampMs = t0 + i * 33L
            )
        }
        val state = tracker.latestState
        assertEquals(PATTERN_WAVE, state.pattern)
    }

    @Test
    fun testHelloWaveGestureDetected() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        var state = tracker.latestState
        for (i in 0 until 22) {
            val x = 0.5f + 0.07f * kotlin.math.sin(i * 0.8f)
            val wrist = Point3D(x, 0.35f, 0f)
            val hand = createMockHand(wrist = wrist, isOpen = true)
            state = tracker.update(
                rightWrist = Pair(x, 0.35f),
                leftWrist = null,
                timestampMs = t0 + i * 33L,
                rightHandLandmarks = hand
            )
        }
        assertEquals("HELLO_MOTION", state.detectedGesture)
        assertTrue(state.gestureConfidence >= 0.88f)
    }

    @Test
    fun testJCurveTrajectoryDetected() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        var state = tracker.latestState

        // Simulate J curve: downward drop (frames 0..10), then leftward/upward hook (frames 11..16)
        for (i in 0..16) {
            val (px, py) = when {
                i <= 10 -> Pair(0.5f, 0.30f + i * 0.015f) // drop from y=0.30 to y=0.45
                else -> {
                    val step = (i - 10)
                    Pair(0.5f - step * 0.010f, 0.45f - step * 0.005f) // hook left and slightly up
                }
            }
            val wrist = Point3D(px, py + 0.12f, 0f)
            val pinkyTip = Point3D(px, py, 0f)
            val hand = createMockHand(wrist = wrist, pinkyTip = pinkyTip, isIPosture = true)

            state = tracker.update(
                rightWrist = Pair(wrist.x, wrist.y),
                leftWrist = null,
                timestampMs = t0 + i * 33L,
                rightHandLandmarks = hand
            )
        }

        assertEquals("J_MOTION", state.detectedGesture)
        assertTrue(state.gestureConfidence >= 0.85f)
    }

    @Test
    fun testZZigzagTrajectoryDetected() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        var state = tracker.latestState

        // Stroke 1: Right (frames 0..6: x 0.35 -> 0.45, y=0.30)
        // Stroke 2: Diagonal down-left (frames 7..12: x 0.45 -> 0.36, y 0.30 -> 0.38)
        // Stroke 3: Right (frames 13..18: x 0.36 -> 0.46, y=0.38)
        for (i in 0..18) {
            val (ix, iy) = when {
                i <= 6 -> Pair(0.35f + i * 0.018f, 0.30f)
                i <= 12 -> Pair(0.458f - (i - 6) * 0.016f, 0.30f + (i - 6) * 0.013f)
                else -> Pair(0.362f + (i - 12) * 0.017f, 0.38f)
            }
            val wrist = Point3D(ix, iy + 0.14f, 0f)
            val indexTip = Point3D(ix, iy, 0f)
            val hand = createMockHand(wrist = wrist, indexTip = indexTip, isIndexOnly = true)

            state = tracker.update(
                rightWrist = Pair(wrist.x, wrist.y),
                leftWrist = null,
                timestampMs = t0 + i * 33L,
                rightHandLandmarks = hand
            )
        }

        assertEquals("Z_MOTION", state.detectedGesture)
        assertTrue(state.gestureConfidence >= 0.85f)
    }

    @Test
    fun testNoShakeTrajectoryDetected() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        var state = tracker.latestState

        // Horizontal shake: rapid side to side oscillation
        for (i in 0 until 18) {
            val x = 0.5f + 0.045f * kotlin.math.sin(i * 1.1f)
            val wrist = Point3D(x, 0.55f, 0f)
            val indexTip = Point3D(x, 0.40f, 0f)
            val hand = createMockHand(wrist = wrist, indexTip = indexTip, isIndexOnly = true)

            state = tracker.update(
                rightWrist = Pair(x, 0.55f),
                leftWrist = null,
                timestampMs = t0 + i * 33L,
                rightHandLandmarks = hand
            )
        }

        assertEquals("NO_MOTION", state.detectedGesture)
        assertTrue(state.gestureConfidence >= 0.85f)
    }

    @Test
    fun testYouForwardPushDetected() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        var state = tracker.latestState

        // Forward push: hand scale increases over time as hand moves closer to camera
        for (i in 0..12) {
            val scale = 0.10f + i * 0.005f // scale increases from 0.10 to 0.16 (>15%)
            val wrist = Point3D(0.5f, 0.5f, -i * 0.006f)
            val pts = MutableList(21) { Point3D(wrist.x, wrist.y, wrist.z) }
            pts[0] = wrist
            pts[9] = Point3D(wrist.x, wrist.y - scale, wrist.z)
            pts[5] = Point3D(wrist.x - 0.03f, wrist.y - scale * 0.9f, wrist.z)
            pts[8] = Point3D(wrist.x - 0.03f, wrist.y - scale * 1.8f, wrist.z) // index extended

            state = tracker.update(
                rightWrist = Pair(wrist.x, wrist.y),
                leftWrist = null,
                timestampMs = t0 + i * 33L,
                rightHandLandmarks = pts
            )
        }

        assertEquals("YOU_MOTION", state.detectedGesture)
        assertTrue(state.gestureConfidence >= 0.85f)
    }

    @Test
    fun testChestContactDetected() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        var state = tracker.latestState

        for (i in 0..8) {
            val wrist = Point3D(0.5f, 0.45f, 0f)
            val hand = createMockHand(wrist = wrist, isOpen = true)
            state = tracker.update(
                rightWrist = Pair(0.5f, 0.45f),
                leftWrist = null,
                timestampMs = t0 + i * 33L,
                rightHandLandmarks = hand,
                activeAnchorZone = "CHEST_CENTER"
            )
        }

        assertEquals("MY_NAME_IS_MOTION", state.detectedGesture)
        assertTrue(state.gestureConfidence >= 0.85f)
    }

    @Test
    fun testConsumeGestureRefractory() {
        val tracker = HandMotionTracker()
        val t0 = 1000L

        // Trigger wave
        for (i in 0 until 22) {
            val x = 0.5f + 0.07f * kotlin.math.sin(i * 0.8f)
            val wrist = Point3D(x, 0.35f, 0f)
            val hand = createMockHand(wrist = wrist, isOpen = true)
            tracker.update(
                rightWrist = Pair(x, 0.35f),
                leftWrist = null,
                timestampMs = t0 + i * 33L,
                rightHandLandmarks = hand
            )
        }
        assertEquals("HELLO_MOTION", tracker.latestState.detectedGesture)

        // Consume gesture
        tracker.consumeGesture(t0 + 22 * 33L)
        assertNull(tracker.latestState.detectedGesture)

        // Immediately subsequent frame within refractory period does not re-fire
        val nextState = tracker.update(
            rightWrist = Pair(0.5f, 0.35f),
            leftWrist = null,
            timestampMs = t0 + 23 * 33L,
            rightHandLandmarks = createMockHand(isOpen = true)
        )
        assertNull(nextState.detectedGesture)
    }

    @Test
    fun testSignMotionMatching() {
        val tracker = HandMotionTracker()
        val t0 = 1000L
        for (i in 0 until 20) {
            val x = 0.5f + 0.08f * kotlin.math.sin(i * 0.7f)
            tracker.update(
                rightWrist = Pair(x, 0.5f),
                leftWrist = null,
                timestampMs = t0 + i * 33L
            )
        }
        val (matched, boost) = HandMotionTracker.matchSignMotion("HELLO", tracker.latestState)
        assertTrue(matched)
        assertTrue(boost > 1.0f)
    }
}
