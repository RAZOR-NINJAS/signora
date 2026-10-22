package ai.signora

import ai.signora.ml.FeatureExtractor
import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import ai.signora.models.PoseLandmarks
import org.junit.Assert.*
import org.junit.Test

class FeatureExtractorUnitTest {

    @Test
    fun testExtractAslFeaturesEmptyAndUndersized() {
        val emptyFeats = FeatureExtractor.extractAslFeatures(emptyList())
        assertEquals(74, emptyFeats.size)
        assertTrue(emptyFeats.all { it == 0.0f })

        val partialPoints = List(10) { Point3D(0.1f * it, 0.2f * it, 0.0f) }
        val partialFeats = FeatureExtractor.extractAslFeatures(partialPoints)
        assertEquals(74, partialFeats.size)
        assertTrue(partialFeats.all { it == 0.0f })
    }

    @Test
    fun testExtractAslFeaturesHandednessInversion() {
        val basePoints = List(21) { i -> Point3D(0.05f * i + 0.1f, 0.04f * i, 0.01f * i) }
        val rightFeats = FeatureExtractor.extractAslFeatures(basePoints, isRightHand = true)
        val leftFeats = FeatureExtractor.extractAslFeatures(basePoints, isRightHand = false)

        assertEquals(74, rightFeats.size)
        assertEquals(74, leftFeats.size)

        // For left hand, X coords are negated before origin translation, ensuring invariance
        // Features must all be finite
        assertTrue(rightFeats.all { !it.isNaN() && !it.isInfinite() })
        assertTrue(leftFeats.all { !it.isNaN() && !it.isInfinite() })
    }

    @Test
    fun testExtractIslFeaturesDualHandSlots() {
        // 1. Empty hands list
        val emptyIsl = FeatureExtractor.extractIslFeatures(emptyList())
        assertEquals(164, emptyIsl.size)
        assertTrue(emptyIsl.all { it == 0.0f })

        // 2. Single Right Hand
        val ptsR = List(21) { i -> Point3D(0.6f + 0.01f * i, 0.5f + 0.01f * i, 0.0f) }
        val handR = HandLandmarks(points = ptsR, isRightHand = true, isFrontCamera = true)
        val singleR = FeatureExtractor.extractIslFeatures(listOf(handR))
        assertEquals(164, singleR.size)
        // Left hand slot 0..73 should be 0
        assertTrue((0..73).all { singleR[it] == 0.0f })
        // Right hand presence flag (index 163) must be 1.0
        assertEquals(1.0f, singleR[163], 1e-4f)
        assertEquals(0.0f, singleR[162], 1e-4f)

        // 3. Single Left Hand
        val ptsL = List(21) { i -> Point3D(0.3f + 0.01f * i, 0.5f + 0.01f * i, 0.0f) }
        val handL = HandLandmarks(points = ptsL, isRightHand = false, isFrontCamera = true)
        val singleL = FeatureExtractor.extractIslFeatures(listOf(handL))
        assertEquals(164, singleL.size)
        // Left hand presence flag (index 162) must be 1.0
        assertEquals(1.0f, singleL[162], 1e-4f)
        assertEquals(0.0f, singleL[163], 1e-4f)

        // 4. Dual Hands
        val dual = FeatureExtractor.extractIslFeatures(listOf(handL, handR))
        assertEquals(164, dual.size)
        assertEquals(1.0f, dual[162], 1e-4f)
        assertEquals(1.0f, dual[163], 1e-4f)
        // Inter-hand spatial distance (offset 148) must be positive
        assertTrue(dual[148] > 0.1f)
    }

    @Test
    fun testExtractIslFeaturesTwoHandsSameTagSortedByScreenX() {
        val pts1 = List(21) { Point3D(0.2f, 0.5f, 0.0f) } // Left of screen
        val pts2 = List(21) { Point3D(0.8f, 0.5f, 0.0f) } // Right of screen
        val h1 = HandLandmarks(points = pts1, isRightHand = true, isFrontCamera = true)
        val h2 = HandLandmarks(points = pts2, isRightHand = true, isFrontCamera = true)

        val feats = FeatureExtractor.extractIslFeatures(listOf(h2, h1)) // Passed in reverse order
        assertEquals(164, feats.size)
        // Both presence flags set
        assertEquals(1.0f, feats[162], 1e-4f)
        assertEquals(1.0f, feats[163], 1e-4f)
    }

    @Test
    fun testExtractSpatialFeaturesDimensionalityAndPose() {
        val pts = List(21) { Point3D(0.5f + 0.01f * it, 0.5f, 0.0f) }
        val hand = HandLandmarks(points = pts, isRightHand = true, isFrontCamera = true)

        // Null pose
        val nullPoseFeats = FeatureExtractor.extractSpatialFeatures(listOf(hand), null)
        assertEquals(220, nullPoseFeats.size)

        // 33-point realistic pose
        val posePts = List(33) { Point3D(0.5f, 0.4f, 0.0f) }
        val pose = PoseLandmarks(points = posePts, isFrontCamera = true)
        val spatialFeats = FeatureExtractor.extractSpatialFeatures(listOf(hand), pose)
        assertEquals(220, spatialFeats.size)
        assertTrue(spatialFeats.all { !it.isNaN() && !it.isInfinite() })
    }

    @Test
    fun testDetectBodyAnchorNullPoseOpenSpace() {
        val pts = List(21) { Point3D(0.5f, 0.5f, 0.0f) }
        val hand = HandLandmarks(points = pts, isRightHand = true, isFrontCamera = true)
        val anchor = FeatureExtractor.detectBodyAnchor(hand, null)
        assertEquals("OPEN_SPACE", anchor.first)
        assertNull(anchor.second)
    }

    @Test
    fun testExtractSpatialFeaturesPoseImputation() {
        val pts = List(21) { Point3D(0.5f, 0.5f, 0.0f) }
        val hand = HandLandmarks(points = pts, isRightHand = true, isFrontCamera = true)

        // Without passing datasetMean, should impute DEFAULT_SPATIAL_MEANS
        val defaultFeats = FeatureExtractor.extractSpatialFeatures(listOf(hand), null)
        assertEquals(220, defaultFeats.size)
        for (i in 0 until 56) {
            assertEquals(FeatureExtractor.DEFAULT_SPATIAL_MEANS[i], defaultFeats[164 + i], 1e-5f)
        }

        // With custom datasetMean, should impute the exact provided datasetMean
        val customMean = FloatArray(220) { it * 0.1f }
        val customFeats = FeatureExtractor.extractSpatialFeatures(listOf(hand), null, customMean)
        for (i in 164 until 220) {
            assertEquals(customMean[i], customFeats[i], 1e-5f)
        }
    }

    @Test
    fun testExtractSpatialFeaturesHandednessFallback() {
        // Two hands both tagged as isRightHand (common MediaPipe front-camera ambiguity)
        val ptsLeft = List(21) { Point3D(0.2f, 0.5f, 0.0f) } // Left screen position
        val ptsRight = List(21) { Point3D(0.8f, 0.5f, 0.0f) } // Right screen position
        val h1 = HandLandmarks(points = ptsLeft, isRightHand = true, isFrontCamera = true)
        val h2 = HandLandmarks(points = ptsRight, isRightHand = true, isFrontCamera = true)

        val posePts = List(33) { Point3D(0.5f, 0.4f, 0.0f) }
        val pose = PoseLandmarks(points = posePts, isFrontCamera = true)

        val feats = FeatureExtractor.extractSpatialFeatures(listOf(h1, h2), pose)
        assertEquals(220, feats.size)
        // Both left and right hands should be populated in 164D base features
        assertEquals(1.0f, feats[162], 1e-4f) // Left hand present
        assertEquals(1.0f, feats[163], 1e-4f) // Right hand present
    }
}
