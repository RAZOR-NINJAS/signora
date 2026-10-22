package ai.signora.ml

import ai.signora.models.RecognitionResult
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * High-performance, zero-dependency neural MLP inference engine in pure Kotlin.
 *
 * Implements identical LayerNorm and numerical GELU activation passes as PyTorch and NumPy,
 * eliminating the need for bulky C++ / ONNX / NDK runtimes and providing sub-millisecond execution.
 */
class SignoraMLP(
    val mode: String,
    val inDim: Int,
    val numClasses: Int,
    val labels: List<String>,
    val mean: FloatArray,
    val std: FloatArray,
    private val temperature: Float,
    private val w0: Array<FloatArray>,
    private val b0: FloatArray,
    private val lnW0: FloatArray,
    private val lnB0: FloatArray,
    private val w4: Array<FloatArray>,
    private val b4: FloatArray,
    private val lnW4: FloatArray,
    private val lnB4: FloatArray,
    private val w8: Array<FloatArray>,
    private val b8: FloatArray
) {

    fun predict(feats: FloatArray): RecognitionResult {
        val probs = forward(feats)
        if (probs.isEmpty()) {
            return RecognitionResult(
                label = "UNKNOWN",
                confidence = 0f,
                margin = 0f,
                top3 = emptyList()
            )
        }
        var maxIdx = 0
        var maxProb = probs[0]
        var secondMaxProb = 0f

        for (i in 1 until probs.size) {
            val p = probs[i]
            if (p > maxProb) {
                secondMaxProb = maxProb
                maxProb = p
                maxIdx = i
            } else if (p > secondMaxProb) {
                secondMaxProb = p
            }
        }
        val label = if (maxIdx < labels.size) labels[maxIdx] else "UNKNOWN"
        val margin = maxProb - secondMaxProb

        val top3 = probs.indices
            .map { i -> (if (i < labels.size) labels[i] else "UNKNOWN") to probs[i] }
            .sortedByDescending { it.second }
            .take(3)

        return RecognitionResult(
            label = label,
            confidence = maxProb,
            margin = margin,
            top3 = top3
        )
    }

    fun forward(feats: FloatArray): FloatArray {
        if (numClasses <= 0) {
            return FloatArray(0)
        }
        val effectiveInDim = minOf(inDim, feats.size)
        // 1. Scale-invariant standardization: (x - mean) / std
        val xNorm = FloatArray(inDim)
        for (i in 0 until effectiveInDim) {
            val s = if (std[i] > 1e-8f) std[i] else 1.0f
            xNorm[i] = (feats[i] - mean[i]) / s
        }

        // 2. Layer 1: Linear [192, inDim] -> LayerNorm -> GELU
        val hidden1Dim = w0.size
        val h1 = FloatArray(hidden1Dim)
        for (i in 0 until hidden1Dim) {
            var sum = b0[i]
            val row = w0[i]
            for (j in 0 until effectiveInDim) {
                sum += xNorm[j] * row[j]
            }
            h1[i] = sum
        }
        layerNormInPlace(h1, lnW0, lnB0)
        geluInPlace(h1)

        // 3. Layer 2: Linear [112, 192] -> LayerNorm -> GELU
        val hidden2Dim = w4.size
        val h2 = FloatArray(hidden2Dim)
        for (i in 0 until hidden2Dim) {
            var sum = b4[i]
            val row = w4[i]
            for (j in 0 until hidden1Dim) {
                sum += h1[j] * row[j]
            }
            h2[i] = sum
        }
        layerNormInPlace(h2, lnW4, lnB4)
        geluInPlace(h2)

        // 4. Layer 3: Linear [numClasses, 112]
        val logits = FloatArray(numClasses)
        for (i in 0 until numClasses) {
            var sum = b8[i]
            val row = w8[i]
            for (j in 0 until hidden2Dim) {
                sum += h2[j] * row[j]
            }
            logits[i] = sum
        }

        // 5. Softmax with temperature scaling
        val temp = max(0.01f, temperature)
        var maxLogit = logits[0] / temp
        for (i in 1 until numClasses) {
            val scaled = logits[i] / temp
            if (scaled > maxLogit) maxLogit = scaled
        }

        val probs = FloatArray(numClasses)
        var sumExp = 0.0f
        for (i in 0 until numClasses) {
            val e = exp((logits[i] / temp) - maxLogit)
            probs[i] = e
            sumExp += e
        }
        val invSum = if (sumExp > 0f) 1.0f / sumExp else 1.0f
        for (i in 0 until numClasses) {
            probs[i] *= invSum
        }

        return probs
    }

    private fun layerNormInPlace(arr: FloatArray, weight: FloatArray, bias: FloatArray, eps: Float = 1e-5f) {
        val n = arr.size
        var sum = 0f
        for (v in arr) sum += v
        val meanVal = sum / n

        var varSum = 0f
        for (v in arr) {
            val diff = v - meanVal
            varSum += diff * diff
        }
        val variance = varSum / n
        val invStd = 1.0f / sqrt(variance + eps)

        for (i in 0 until n) {
            arr[i] = ((arr[i] - meanVal) * invStd) * weight[i] + bias[i]
        }
    }

    private fun erf(x: Float): Float {
        // Abramowitz & Stegun 7.1.26 (max error < 1.5e-7)
        val sign = if (x < 0f) -1f else 1f
        val absX = kotlin.math.abs(x)
        val t = 1.0f / (1.0f + 0.3275911f * absX)
        val poly = 1.0f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * exp(-absX * absX)
        return sign * poly
    }

    private fun geluInPlace(arr: FloatArray) {
        val invSqrt2 = 0.70710678118f // 1 / sqrt(2)
        for (i in arr.indices) {
            val x = arr[i]
            arr[i] = 0.5f * x * (1.0f + erf(x * invSqrt2))
        }
    }

    companion object {
        fun fromJson(jsonStr: String): SignoraMLP {
            val root = JSONObject(jsonStr)
            val mode = root.optString("mode", "asl")
            val inDim = root.getInt("in_dim")
            val numClasses = root.getInt("num_classes")
            val temperature = root.optDouble("temperature", 1.0).toFloat()

            val labelsJson = root.getJSONArray("labels")
            val labels = List(labelsJson.length()) { labelsJson.getString(it) }

            val meanJson = root.getJSONArray("mean")
            val mean = FloatArray(meanJson.length()) { meanJson.getDouble(it).toFloat() }

            val stdJson = root.getJSONArray("std")
            val std = FloatArray(stdJson.length()) { stdJson.getDouble(it).toFloat() }

            fun parse2D(key: String): Array<FloatArray> {
                val outer = root.getJSONArray(key)
                return Array(outer.length()) { i ->
                    val inner = outer.getJSONArray(i)
                    FloatArray(inner.length()) { j -> inner.getDouble(j).toFloat() }
                }
            }

            fun parse1D(key: String): FloatArray {
                val arr = root.getJSONArray(key)
                return FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
            }

            return SignoraMLP(
                mode = mode,
                inDim = inDim,
                numClasses = numClasses,
                labels = labels,
                mean = mean,
                std = std,
                temperature = temperature,
                w0 = parse2D("net_0_weight"),
                b0 = parse1D("net_0_bias"),
                lnW0 = parse1D("net_1_weight"),
                lnB0 = parse1D("net_1_bias"),
                w4 = parse2D("net_4_weight"),
                b4 = parse1D("net_4_bias"),
                lnW4 = parse1D("net_5_weight"),
                lnB4 = parse1D("net_5_bias"),
                w8 = parse2D("net_8_weight"),
                b8 = parse1D("net_8_bias")
            )
        }
    }
}
