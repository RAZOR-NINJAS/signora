package ai.signora

import ai.signora.ml.BodySegmentation
import ai.signora.models.Point3D
import ai.signora.models.PoseLandmarks
import org.junit.Assert.*
import org.junit.Test

class BodySegmentationUnitTest {

    @Test
    fun testComputeSegmentsNullAndUndersizedPose() {
        assertTrue(BodySegmentation.computeSegments(null).isEmpty())

        val partialPose = PoseLandmarks(points = List(15) { Point3D(0.5f, 0.5f, 0.0f) }, isFrontCamera = true)
        assertTrue(BodySegmentation.computeSegments(partialPose).isEmpty())
    }

    @Test
    fun testComputeSegmentsValidPose() {
        // Construct standard 33-point pose
        val pts = MutableList(33) { Point3D(0.5f, 0.5f, 0.0f) }
        pts[0] = Point3D(0.50f, 0.25f, 0.0f)  // Nose
        pts[9] = Point3D(0.48f, 0.32f, 0.0f)  // Mouth Left
        pts[10] = Point3D(0.52f, 0.32f, 0.0f) // Mouth Right
        pts[11] = Point3D(0.35f, 0.45f, 0.0f) // Left Shoulder
        pts[12] = Point3D(0.65f, 0.45f, 0.0f) // Right Shoulder
        pts[13] = Point3D(0.28f, 0.60f, 0.0f) // Left Elbow
        pts[14] = Point3D(0.72f, 0.60f, 0.0f) // Right Elbow
        pts[15] = Point3D(0.30f, 0.75f, 0.0f) // Left Wrist
        pts[16] = Point3D(0.70f, 0.75f, 0.0f) // Right Wrist
        pts[23] = Point3D(0.40f, 0.85f, 0.0f) // Left Hip
        pts[24] = Point3D(0.60f, 0.85f, 0.0f) // Right Hip

        val pose = PoseLandmarks(points = pts, isFrontCamera = true)
        val segments = BodySegmentation.computeSegments(pose)

        assertTrue(segments.size >= 8)

        val head = segments.firstOrNull { it.id == "HEAD" }
        assertNotNull(head)
        assertTrue(head!!.matches("FOREHEAD"))
        assertTrue(head.matches("CROWN"))

        val chest = segments.firstOrNull { it.id == "CHEST_CENTER" }
        assertNotNull(chest)
        assertTrue(chest!!.matches("CHEST"))
        assertTrue(chest.matches("STERNUM"))

        val chin = segments.firstOrNull { it.id == "CHIN" }
        assertNotNull(chin)
        assertTrue(chin!!.matches("MOUTH"))
    }

    @Test
    fun testSegmentContainsAndDistance() {
        val pts = MutableList(33) { Point3D(0.5f, 0.5f, 0.0f) }
        pts[11] = Point3D(0.35f, 0.45f, 0.0f)
        pts[12] = Point3D(0.65f, 0.45f, 0.0f)
        val pose = PoseLandmarks(points = pts, isFrontCamera = true)
        val segments = BodySegmentation.computeSegments(pose)
        val head = segments.first { it.id == "HEAD" }

        // Exact center should be contained and have distance near 0
        assertTrue(head.contains(head.center))
        assertEquals(0.0f, head.normalizedDistance(head.center), 1e-4f)

        // Far point should not be contained
        val farPoint = Point3D(0.99f, 0.99f, 0.0f)
        assertFalse(head.contains(farPoint))
        assertTrue(head.normalizedDistance(farPoint) > 1.5f)
    }

    @Test
    fun testDetectMultipleActiveSegments() {
        val pts = MutableList(33) { Point3D(0.5f, 0.5f, 0.0f) }
        pts[0] = Point3D(0.50f, 0.25f, 0.0f)
        pts[9] = Point3D(0.48f, 0.32f, 0.0f)
        pts[10] = Point3D(0.52f, 0.32f, 0.0f)
        pts[11] = Point3D(0.35f, 0.45f, 0.0f)
        pts[12] = Point3D(0.65f, 0.45f, 0.0f)
        val pose = PoseLandmarks(points = pts, isFrontCamera = true)
        val segments = BodySegmentation.computeSegments(pose)

        val chestL = segments.first { it.id == "CHEST_LEFT" }
        val chestR = segments.first { it.id == "CHEST_RIGHT" }

        // Hand 1 near Left Chest
        val hand1Pts = List(21) { chestL.center }
        val hand1 = ai.signora.models.HandLandmarks(hand1Pts, isRightHand = false, isFrontCamera = true)

        // Hand 2 near Right Chest
        val hand2Pts = List(21) { chestR.center }
        val hand2 = ai.signora.models.HandLandmarks(hand2Pts, isRightHand = true, isFrontCamera = true)

        val active = BodySegmentation.detectActiveSegments(listOf(hand1, hand2), pose)
        assertEquals(2, active.size)
        val activeIds = active.map { it.id }.toSet()
        assertTrue(activeIds.contains("CHEST_LEFT"))
        assertTrue(activeIds.contains("CHEST_RIGHT"))

        // Single segment backward-compatible method returns the first one
        val single = BodySegmentation.detectActiveSegment(listOf(hand1, hand2), pose)
        assertNotNull(single)
        assertTrue(single!!.id in listOf("CHEST_LEFT", "CHEST_RIGHT"))
    }
}
