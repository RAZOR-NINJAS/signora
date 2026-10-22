package ai.signora.training

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * High-performance, zero-dependency on-device neural training engine in pure Kotlin.
 *
 * Trains a 220D spatial multi-class MLP with LayerNorm and GELU activations directly on Android,
 * optimizing newly recorded signs without requiring Python, PyTorch, or external servers.
 */
class OnDeviceIslTrainer(private val context: Context) {

    data class TrainingProgress(
        val epoch: Int,
        val totalEpochs: Int,
        val loss: Float,
        val accuracy: Float,
        val message: String
    )

    data class TrainingSummary(
        val success: Boolean,
        val totalClasses: Int,
        val totalSamples: Int,
        val labels: List<String>,
        val finalAccuracy: Float,
        val finalLoss: Float,
        val durationMs: Long,
        val errorMessage: String? = null
    )

    suspend fun train(
        dataset: Map<String, List<FloatArray>>,
        epochs: Int = 40,
        batchSize: Int = 64,
        onProgress: (TrainingProgress) -> Unit = {}
    ): TrainingSummary = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        val labels = dataset.keys.filter { dataset[it]?.isNotEmpty() == true }.sorted()
        val numClasses = labels.size

        if (numClasses < 2) {
            return@withContext TrainingSummary(
                success = false,
                totalClasses = numClasses,
                totalSamples = dataset.values.sumOf { it.size },
                labels = labels,
                finalAccuracy = 0f,
                finalLoss = 0f,
                durationMs = 0,
                errorMessage = "At least 2 distinct signs with samples are required for classification."
            )
        }

        val inDim = 220
        val hidden1 = 128
        val hidden2 = 64

        // 1. Flatten dataset into memory
        val allSamples = mutableListOf<FloatArray>()
        val allTargets = mutableListOf<Int>()

        for ((classIdx, label) in labels.withIndex()) {
            val samples = dataset[label] ?: continue
            for (s in samples) {
                if (s.size >= inDim) {
                    allSamples.add(s)
                    allTargets.add(classIdx)
                }
            }
        }

        val nSamples = allSamples.size
        if (nSamples < 10) {
            return@withContext TrainingSummary(
                success = false,
                totalClasses = numClasses,
                totalSamples = nSamples,
                labels = labels,
                finalAccuracy = 0f,
                finalLoss = 0f,
                durationMs = 0,
                errorMessage = "Insufficient samples to train (minimum 10 required)."
            )
        }

        // 2. Compute Mean & Standard Deviation for standardization
        val mean = FloatArray(inDim)
        val std = FloatArray(inDim)

        for (s in allSamples) {
            for (i in 0 until inDim) {
                mean[i] += s[i]
            }
        }
        for (i in 0 until inDim) {
            mean[i] /= nSamples
        }

        for (s in allSamples) {
            for (i in 0 until inDim) {
                val diff = s[i] - mean[i]
                std[i] += diff * diff
            }
        }
        for (i in 0 until inDim) {
            val variance = std[i] / nSamples
            std[i] = if (variance > 1e-6f) sqrt(variance) else 1.0f
        }

        // Standardize in-place
        val xData = Array(nSamples) { idx ->
            val raw = allSamples[idx]
            FloatArray(inDim) { i -> (raw[i] - mean[i]) / std[i] }
        }
        val yData = allTargets.toIntArray()

        // 3. Weight Initialization (He Normal)
        val rng = Random(42)
        fun initWeights(rows: Int, cols: Int): Array<FloatArray> {
            val scale = sqrt(2.0f / cols)
            return Array(rows) { FloatArray(cols) { (rng.nextFloat() * 2f - 1f) * scale } }
        }

        val w0 = initWeights(hidden1, inDim)
        val b0 = FloatArray(hidden1)
        val lnW0 = FloatArray(hidden1) { 1.0f }
        val lnB0 = FloatArray(hidden1) { 0.0f }

        val w4 = initWeights(hidden2, hidden1)
        val b4 = FloatArray(hidden2)
        val lnW4 = FloatArray(hidden2) { 1.0f }
        val lnB4 = FloatArray(hidden2) { 0.0f }

        val w8 = initWeights(numClasses, hidden2)
        val b8 = FloatArray(numClasses)

        // Momentum buffers
        val vw0 = Array(hidden1) { FloatArray(inDim) }
        val vb0 = FloatArray(hidden1)
        val vw4 = Array(hidden2) { FloatArray(hidden1) }
        val vb4 = FloatArray(hidden2)
        val vw8 = Array(numClasses) { FloatArray(hidden2) }
        val vb8 = FloatArray(numClasses)

        val momentum = 0.90f
        var lr = 0.05f

        var finalLoss = 0f
        var finalAcc = 0f

        // 4. Training Loop (Mini-Batch SGD with Momentum)
        val indices = IntArray(nSamples) { it }

        for (epoch in 1..epochs) {
            // Shuffle
            indices.shuffle(rng)

            var epochLoss = 0f
            var correctCount = 0

            var batchStart = 0
            while (batchStart < nSamples) {
                val batchEnd = minOf(batchStart + batchSize, nSamples)
                val curBatchSize = batchEnd - batchStart

                // Accumulate batch gradients
                val gw0 = Array(hidden1) { FloatArray(inDim) }
                val gb0 = FloatArray(hidden1)
                val gw4 = Array(hidden2) { FloatArray(hidden1) }
                val gb4 = FloatArray(hidden2)
                val gw8 = Array(numClasses) { FloatArray(hidden2) }
                val gb8 = FloatArray(numClasses)

                for (bIdx in batchStart until batchEnd) {
                    val sIdx = indices[bIdx]
                    val x = xData[sIdx]
                    val target = yData[sIdx]

                    // Forward Layer 1: Linear -> LayerNorm -> GELU
                    val z0 = FloatArray(hidden1)
                    for (i in 0 until hidden1) {
                        var sum = b0[i]
                        val row = w0[i]
                        for (j in 0 until inDim) sum += x[j] * row[j]
                        z0[i] = sum
                    }
                    val h1 = z0.copyOf()
                    applyLayerNorm(h1, lnW0, lnB0)
                    applyGelu(h1)

                    // Forward Layer 2: Linear -> LayerNorm -> GELU
                    val z4 = FloatArray(hidden2)
                    for (i in 0 until hidden2) {
                        var sum = b4[i]
                        val row = w4[i]
                        for (j in 0 until hidden1) sum += h1[j] * row[j]
                        z4[i] = sum
                    }
                    val h2 = z4.copyOf()
                    applyLayerNorm(h2, lnW4, lnB4)
                    applyGelu(h2)

                    // Forward Output Layer: Linear -> Softmax
                    val logits = FloatArray(numClasses)
                    var maxLogit = -1e9f
                    for (i in 0 until numClasses) {
                        var sum = b8[i]
                        val row = w8[i]
                        for (j in 0 until hidden2) sum += h2[j] * row[j]
                        logits[i] = sum
                        if (sum > maxLogit) maxLogit = sum
                    }

                    var expSum = 0f
                    val probs = FloatArray(numClasses)
                    for (i in 0 until numClasses) {
                        probs[i] = exp(logits[i] - maxLogit)
                        expSum += probs[i]
                    }
                    var predClass = 0
                    var maxP = 0f
                    for (i in 0 until numClasses) {
                        probs[i] /= expSum
                        if (probs[i] > maxP) {
                            maxP = probs[i]
                            predClass = i
                        }
                    }

                    if (predClass == target) correctCount++
                    val sampleLoss = -ln(maxOf(probs[target], 1e-7f))
                    epochLoss += sampleLoss

                    // Backpropagation
                    // dLoss / dLogits = probs - one_hot(target)
                    val dLogits = FloatArray(numClasses)
                    for (i in 0 until numClasses) {
                        dLogits[i] = probs[i] - (if (i == target) 1.0f else 0.0f)
                    }

                    // Gradients for Layer 3 (w8, b8)
                    val dh2 = FloatArray(hidden2)
                    for (i in 0 until numClasses) {
                        val dL = dLogits[i]
                        gb8[i] += dL
                        val wRow = w8[i]
                        val gwRow = gw8[i]
                        for (j in 0 until hidden2) {
                            gwRow[j] += dL * h2[j]
                            dh2[j] += dL * wRow[j]
                        }
                    }

                    // Backprop through GELU + LayerNorm (effective proxy: positive pass derivative)
                    val dz4 = FloatArray(hidden2)
                    for (j in 0 until hidden2) {
                        val gPrime = if (h2[j] > 0f) 1.0f else 0.05f
                        dz4[j] = dh2[j] * gPrime
                    }

                    // Gradients for Layer 2 (w4, b4)
                    val dh1 = FloatArray(hidden1)
                    for (i in 0 until hidden2) {
                        val dZ = dz4[i]
                        gb4[i] += dZ
                        val wRow = w4[i]
                        val gwRow = gw4[i]
                        for (j in 0 until hidden1) {
                            gwRow[j] += dZ * h1[j]
                            dh1[j] += dZ * wRow[j]
                        }
                    }

                    // Backprop through GELU + LayerNorm
                    val dz0 = FloatArray(hidden1)
                    for (j in 0 until hidden1) {
                        val gPrime = if (h1[j] > 0f) 1.0f else 0.05f
                        dz0[j] = dh1[j] * gPrime
                    }

                    // Gradients for Layer 1 (w0, b0)
                    for (i in 0 until hidden1) {
                        val dZ = dz0[i]
                        gb0[i] += dZ
                        val gwRow = gw0[i]
                        for (j in 0 until inDim) {
                            gwRow[j] += dZ * x[j]
                        }
                    }
                }

                // Apply Momentum Updates
                val invB = 1.0f / curBatchSize
                for (i in 0 until numClasses) {
                    vb8[i] = momentum * vb8[i] + lr * (gb8[i] * invB)
                    b8[i] -= vb8[i]
                    val wRow = w8[i]
                    val vwRow = vw8[i]
                    val gwRow = gw8[i]
                    for (j in 0 until hidden2) {
                        vwRow[j] = momentum * vwRow[j] + lr * (gwRow[j] * invB + 1e-4f * wRow[j])
                        wRow[j] -= vwRow[j]
                    }
                }

                for (i in 0 until hidden2) {
                    vb4[i] = momentum * vb4[i] + lr * (gb4[i] * invB)
                    b4[i] -= vb4[i]
                    val wRow = w4[i]
                    val vwRow = vw4[i]
                    val gwRow = gw4[i]
                    for (j in 0 until hidden1) {
                        vwRow[j] = momentum * vwRow[j] + lr * (gwRow[j] * invB + 1e-4f * wRow[j])
                        wRow[j] -= vwRow[j]
                    }
                }

                for (i in 0 until hidden1) {
                    vb0[i] = momentum * vb0[i] + lr * (gb0[i] * invB)
                    b0[i] -= vb0[i]
                    val wRow = w0[i]
                    val vwRow = vw0[i]
                    val gwRow = gw0[i]
                    for (j in 0 until inDim) {
                        vwRow[j] = momentum * vwRow[j] + lr * (gwRow[j] * invB + 1e-4f * wRow[j])
                        wRow[j] -= vwRow[j]
                    }
                }

                batchStart = batchEnd
            }

            finalLoss = epochLoss / nSamples
            finalAcc = correctCount.toFloat() / nSamples
            lr *= 0.97f // Learning rate annealing

            onProgress(
                TrainingProgress(
                    epoch = epoch,
                    totalEpochs = epochs,
                    loss = finalLoss,
                    accuracy = finalAcc,
                    message = "Epoch $epoch/$epochs • Loss: ${String.format("%.3f", finalLoss)} • Acc: ${String.format("%.1f", finalAcc * 100)}%"
                )
            )
        }

        // 5. Build and Save `isl_model.json`
        val root = JSONObject().apply {
            put("format", "signora_mlp_v1")
            put("mode", "isl")
            put("in_dim", inDim)
            put("num_classes", numClasses)
            put("temperature", 1.0)
            put("is_custom_trained", true)

            val labelsArray = JSONArray()
            for (l in labels) labelsArray.put(l)
            put("labels", labelsArray)

            val meanArray = JSONArray()
            for (m in mean) meanArray.put(m.toDouble())
            put("mean", meanArray)

            val stdArray = JSONArray()
            for (s in std) stdArray.put(s.toDouble())
            put("std", stdArray)

            put("net_0_weight", to2DJson(w0))
            put("net_0_bias", to1DJson(b0))
            put("net_1_weight", to1DJson(lnW0))
            put("net_1_bias", to1DJson(lnB0))

            put("net_4_weight", to2DJson(w4))
            put("net_4_bias", to1DJson(b4))
            put("net_5_weight", to1DJson(lnW4))
            put("net_5_bias", to1DJson(lnB4))

            put("net_8_weight", to2DJson(w8))
            put("net_8_bias", to1DJson(b8))
        }

        val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
        val targetFile = File(modelsDir, "isl_model.json")
        val backupFile = File(modelsDir, "isl_model.json.bak")

        if (targetFile.exists()) {
            try {
                targetFile.copyTo(backupFile, overwrite = true)
            } catch (_: Exception) {}
        }

        targetFile.writeText(root.toString(), StandardCharsets.UTF_8)

        val duration = System.currentTimeMillis() - startTime

        TrainingSummary(
            success = true,
            totalClasses = numClasses,
            totalSamples = nSamples,
            labels = labels,
            finalAccuracy = finalAcc,
            finalLoss = finalLoss,
            durationMs = duration
        )
    }

    private fun to1DJson(arr: FloatArray): JSONArray {
        val json = JSONArray()
        for (v in arr) json.put(v.toDouble())
        return json
    }

    private fun to2DJson(arr: Array<FloatArray>): JSONArray {
        val json = JSONArray()
        for (row in arr) {
            val rJson = JSONArray()
            for (v in row) rJson.put(v.toDouble())
            json.put(rJson)
        }
        return json
    }

    private fun applyLayerNorm(arr: FloatArray, weight: FloatArray, bias: FloatArray) {
        val n = arr.size
        var sum = 0f
        for (v in arr) sum += v
        val meanVal = sum / n

        var varSum = 0f
        for (v in arr) {
            val diff = v - meanVal
            varSum += diff * diff
        }
        val invStd = 1.0f / sqrt((varSum / n) + 1e-5f)
        for (i in 0 until n) {
            arr[i] = ((arr[i] - meanVal) * invStd) * weight[i] + bias[i]
        }
    }

    private fun applyGelu(arr: FloatArray) {
        for (i in arr.indices) {
            val x = arr[i]
            // Fast GELU approximation: 0.5 * x * (1 + tanh(sqrt(2/pi) * (x + 0.044715 * x^3)))
            val c = 0.79788456f * (x + 0.044715f * x * x * x)
            val t = kotlin.math.tanh(c)
            arr[i] = 0.5f * x * (1.0f + t)
        }
    }
}
