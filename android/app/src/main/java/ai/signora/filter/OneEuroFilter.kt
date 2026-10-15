package ai.signora.filter

import ai.signora.models.Point3D
import kotlin.math.PI
import kotlin.math.abs

/**
 * 1D Low-pass filter component for One-Euro filtering.
 */
class LowPassFilter {
    private var initialized: Boolean = false
    private var hatXPrev: Float = 0f

    fun filter(x: Float, alpha: Float): Float {
        if (!initialized) {
            hatXPrev = x
            initialized = true
            return x
        }
        val hatX = alpha * x + (1f - alpha) * hatXPrev
        hatXPrev = hatX
        return hatX
    }

    fun lastValue(): Float = hatXPrev

    fun reset() {
        initialized = false
        hatXPrev = 0f
    }
}

/**
 * One Euro Filter for low-latency, jitter-free signal filtering (Casiez et al., 2012).
 *
 * Automatically adapts cutoff frequency based on velocity:
 * - At low speed: Low cutoff frequency eliminates subtle jitter during dwell holding.
 * - At high speed: Increases cutoff frequency to avoid phase lag during rapid hand transitions.
 */
class OneEuroFilter(
    var minCutoff: Float = 1.0f,
    var beta: Float = 0.007f,
    var dCutoff: Float = 1.0f
) {
    private val xFilter = LowPassFilter()
    private val dxFilter = LowPassFilter()
    private var lastTimestampMs: Long = -1L

    private fun alpha(rate: Float, cutoff: Float): Float {
        val tau = 1.0f / (2f * PI.toFloat() * cutoff)
        val te = 1.0f / rate
        return 1.0f / (1.0f + tau / te)
    }

    fun filter(x: Float, timestampMs: Long): Float {
        if (lastTimestampMs < 0L) {
            lastTimestampMs = timestampMs
            return xFilter.filter(x, 1f)
        }
        val dt = (timestampMs - lastTimestampMs) / 1000f
        lastTimestampMs = timestampMs
        val rate = if (dt > 1e-4f) 1f / dt else 30f

        val prevX = xFilter.lastValue()
        val dx = (x - prevX) * rate
        val edx = dxFilter.filter(dx, alpha(rate, dCutoff))
        val cutoff = minCutoff + beta * abs(edx)
        return xFilter.filter(x, alpha(rate, cutoff))
    }

    fun reset() {
        lastTimestampMs = -1L
        xFilter.reset()
        dxFilter.reset()
    }
}

/**
 * Multi-dimensional One Euro filter for 3D landmarks (supports 21 hand points or 33 pose points).
 */
class LandmarkSmoother(
    val maxPoints: Int = 33,
    minCutoff: Float = 1.0f,
    beta: Float = 0.05f,
    dCutoff: Float = 1.0f
) {
    private val xFilters = Array(maxPoints) { OneEuroFilter(minCutoff, beta, dCutoff) }
    private val yFilters = Array(maxPoints) { OneEuroFilter(minCutoff, beta, dCutoff) }
    private val zFilters = Array(maxPoints) { OneEuroFilter(minCutoff, beta, dCutoff) }

    fun filter(rawPoints: List<Point3D>, timestampMs: Long): List<Point3D> {
        val n = minOf(rawPoints.size, maxPoints)
        if (n == 0) return rawPoints
        val result = ArrayList<Point3D>(rawPoints.size)
        for (i in 0 until n) {
            val p = rawPoints[i]
            result.add(
                Point3D(
                    x = xFilters[i].filter(p.x, timestampMs),
                    y = yFilters[i].filter(p.y, timestampMs),
                    z = zFilters[i].filter(p.z, timestampMs)
                )
            )
        }
        for (i in n until rawPoints.size) {
            result.add(rawPoints[i])
        }
        return result
    }

    fun reset() {
        for (i in 0 until maxPoints) {
            xFilters[i].reset()
            yFilters[i].reset()
            zFilters[i].reset()
        }
    }
}
