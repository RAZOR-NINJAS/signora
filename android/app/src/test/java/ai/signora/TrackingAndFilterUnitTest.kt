package ai.signora

import ai.signora.filter.OneEuroFilter
import ai.signora.ml.FeatureExtractor
import ai.signora.models.Point3D
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TrackingAndFilterUnitTest {

    @Test
    fun testFeatureTranslationInvariance() {
        val basePoints = List(21) { i ->
            Point3D(0.1f * i, 0.05f * i, 0.02f * i)
        }
        val shiftedPoints = List(21) { i ->
            Point3D(basePoints[i].x + 0.35f, basePoints[i].y - 0.22f, basePoints[i].z + 0.50f)
        }

        val feat1 = FeatureExtractor.extractAslFeatures(basePoints, true)
        val feat2 = FeatureExtractor.extractAslFeatures(shiftedPoints, true)

        assertEquals("Features must have 74 dimensions", 74, feat1.size)
        assertEquals("Features must have 74 dimensions", 74, feat2.size)

        for (i in 0 until 74) {
            assertEquals("Feature $i must be translation invariant", feat1[i], feat2[i], 1e-4f)
        }
    }

    @Test
    fun testFeatureScaleInvariance() {
        val basePoints = List(21) { i ->
            Point3D(0.1f * i, 0.05f * i, 0.02f * i)
        }
        val scaledPoints = List(21) { i ->
            Point3D(basePoints[i].x * 2.5f, basePoints[i].y * 2.5f, basePoints[i].z * 2.5f)
        }

        val feat1 = FeatureExtractor.extractAslFeatures(basePoints, true)
        val feat2 = FeatureExtractor.extractAslFeatures(scaledPoints, true)

        for (i in 0 until 74) {
            assertEquals("Feature $i must be scale invariant", feat1[i], feat2[i], 1e-4f)
        }
    }

    @Test
    fun testOneEuroFilterJitterSuppression() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.007f)
        val steadyValue = 0.5f

        // Initial sample
        filter.filter(steadyValue, 0L)

        // Inject high frequency low amplitude jitter: 0.505, 0.495
        var filteredTotalChange = 0f
        var rawTotalChange = 0f
        var prevFiltered = steadyValue
        var prevRaw = steadyValue

        for (step in 1..20) {
            val noise = if (step % 2 == 0) 0.015f else -0.015f
            val raw = steadyValue + noise
            val filtered = filter.filter(raw, step * 33L)

            filteredTotalChange += abs(filtered - prevFiltered)
            rawTotalChange += abs(raw - prevRaw)
            prevFiltered = filtered
            prevRaw = raw
        }

        assertTrue("Filtered jitter must be significantly smaller than raw jitter", filteredTotalChange < rawTotalChange * 0.45f)
    }

    @Test
    fun testOneEuroFilterHighVelocityResponsiveness() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.007f)
        filter.filter(0.0f, 0L)

        // Rapid step change: jumps to 1.0 at high velocity
        var lastFiltered = 0f
        for (step in 1..10) {
            lastFiltered = filter.filter(1.0f, step * 33L)
        }

        // Within 10 frames (~330ms), should converge close to target
        assertTrue("Filter must track fast step without excessive lag", lastFiltered > 0.85f)
    }

    @Test
    fun testOneEuroFilterBackwardsTimestamp() {
        val filter = OneEuroFilter()
        val first = filter.filter(0.5f, 1000L)
        assertEquals(0.5f, first, 1e-4f)

        // Advance normally
        val second = filter.filter(0.52f, 1033L)
        assertTrue(second > 0.49f && second < 0.55f)

        // Backwards timestamp: should snap and not produce NaN or crash
        val reversed = filter.filter(0.8f, 500L)
        assertEquals(0.8f, reversed, 1e-4f)
    }
}
