package ai.signora.recovery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-healing asset and model dependency manager.
 *
 * Ensures all neural models and MediaPipe task files are verified and present.
 * If any model is missing from the APK bundle or corrupted, it automatically downloads
 * the required model assets in the background from official CDN releases.
 */
class AutoAssetManager(
    private val context: Context? = null,
    baseDir: File? = null,
    private val assetLoader: ((String) -> String)? = null
) {

    private val modelsDir = baseDir ?: File(context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "models"), "models").apply { mkdirs() }

    data class AssetStatus(
        val isReady: Boolean,
        val message: String,
        val progressPercent: Int = 100
    )

    private val assetUrls = mapOf(
        "hand_landmarker.task" to "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task",
        "pose_landmarker.task" to "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task",
        "asl_model.json" to "https://github.com/RAZOR-NINJAS/signora/releases/download/v0.1.2/asl_model.json",
        "isl_model.json" to "https://github.com/RAZOR-NINJAS/signora/releases/download/v0.1.2/isl_model.json"
    )

    suspend fun ensureModelAssets(
        onProgress: (AssetStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val requiredAssets = listOf("hand_landmarker.task", "pose_landmarker.task", "asl_model.json", "isl_model.json")

        for ((idx, filename) in requiredAssets.withIndex()) {
            val targetFile = File(modelsDir, filename)

            // For JSON models, verify cached file is not shadowing a richer APK bundled model
            if (targetFile.exists() && targetFile.length() > 0 && filename.endsWith(".json")) {
                var needsBundledOverwrite = false
                try {
                    val bundledStr = loadBundledModelJsonString(filename)
                    val bundledObj = org.json.JSONObject(bundledStr)
                    val cachedObj = org.json.JSONObject(targetFile.readText(Charsets.UTF_8))
                    val bundledClasses = bundledObj.optInt("num_classes", bundledObj.optJSONArray("labels")?.length() ?: 0)
                    val cachedClasses = cachedObj.optInt("num_classes", cachedObj.optJSONArray("labels")?.length() ?: 0)
                    val bundledInDim = bundledObj.optInt("in_dim", 0)
                    val cachedInDim = cachedObj.optInt("in_dim", 0)
                    val hasWeights = cachedObj.has("net_0_weight") && cachedObj.has("mean") && cachedObj.has("std")
                    val isCustomTrained = cachedObj.optBoolean("is_custom_trained", false)

                    // Overwrite if cached model has fewer classes, lower dimensionality, lacks weights,
                    // or is not explicitly custom-trained and has same class count as bundled asset.
                    if (cachedClasses < bundledClasses || cachedInDim < bundledInDim || !hasWeights) {
                        needsBundledOverwrite = true
                    } else if (cachedClasses == bundledClasses && !isCustomTrained) {
                        needsBundledOverwrite = true
                    }
                } catch (_: Exception) {
                    needsBundledOverwrite = true
                }

                if (!needsBundledOverwrite) {
                    continue
                }
            } else if (targetFile.exists() && targetFile.length() > 0) {
                continue
            }

            // 2. Attempt extraction from APK bundled assets
            var copiedFromApk = false
            try {
                if (assetLoader != null) {
                    val bundledStr = assetLoader.invoke(filename)
                    targetFile.writeText(bundledStr, Charsets.UTF_8)
                    copiedFromApk = true
                } else if (context != null) {
                    context.assets.open("models/$filename").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    copiedFromApk = true
                }
            } catch (_: Exception) {
                copiedFromApk = false
            }

            if (copiedFromApk && targetFile.length() > 0) {
                continue
            }

            // 3. Self-Healing Fallback: Auto-Download from CDN
            val downloadUrl = assetUrls[filename]
            if (downloadUrl != null) {
                onProgress(
                    AssetStatus(
                        isReady = false,
                        message = "Auto-installing model dependency: $filename...",
                        progressPercent = (idx * 100) / requiredAssets.size
                    )
                )

                val success = downloadFile(downloadUrl, targetFile)
                if (!success) {
                    onProgress(
                        AssetStatus(
                            isReady = false,
                            message = "Failed to download $filename. Check internet connection.",
                            progressPercent = 0
                        )
                    )
                    return@withContext false
                }
            }
        }

        onProgress(AssetStatus(isReady = true, message = "All neural models active and verified.", progressPercent = 100))
        return@withContext true
    }

    fun getModelFile(filename: String): File {
        val f = File(modelsDir, filename)

        // Always prioritize fresh extraction from bundled APK assets
        try {
            if (assetLoader != null) {
                val bundledStr = assetLoader.invoke(filename)
                f.writeText(bundledStr, Charsets.UTF_8)
                return f
            } else if (context != null) {
                context.assets.open("models/$filename").use { input ->
                    FileOutputStream(f).use { output ->
                        input.copyTo(output)
                    }
                }
                return f
            }
        } catch (_: Exception) {}

        if (f.exists() && f.length() > 0) return f
        return f
    }

    fun loadBundledModelJsonString(filename: String): String {
        if (assetLoader != null) {
            return assetLoader.invoke(filename)
        }
        val ctx = context ?: throw IllegalStateException("Context is null and assetLoader not provided")
        return ctx.assets.open("models/$filename").bufferedReader().use { it.readText() }
    }

    fun loadModelJsonString(filename: String, forceBundled: Boolean = false): String {
        val f = File(modelsDir, filename)
        var bundledJson: String? = null
        try {
            bundledJson = loadBundledModelJsonString(filename)
        } catch (_: Exception) {}

        if (forceBundled && bundledJson != null) {
            try {
                f.writeText(bundledJson, Charsets.UTF_8)
            } catch (_: Exception) {}
            return bundledJson
        }

        if (f.exists() && f.length() > 0) {
            val cachedJson = try {
                f.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }

            if (cachedJson != null && bundledJson != null && filename.endsWith(".json")) {
                try {
                    val cachedObj = org.json.JSONObject(cachedJson)
                    val bundledObj = org.json.JSONObject(bundledJson)

                    val cachedClasses = cachedObj.optInt("num_classes", cachedObj.optJSONArray("labels")?.length() ?: 0)
                    val bundledClasses = bundledObj.optInt("num_classes", bundledObj.optJSONArray("labels")?.length() ?: 0)
                    val cachedInDim = cachedObj.optInt("in_dim", 0)
                    val bundledInDim = bundledObj.optInt("in_dim", 0)
                    val hasWeights = cachedObj.has("net_0_weight") && cachedObj.has("mean") && cachedObj.has("std")
                    val isCustomTrained = cachedObj.optBoolean("is_custom_trained", false)

                    // If cached model has fewer classes, lower dimensionality, or fails validation,
                    // restore from APK bundled asset
                    if (cachedClasses < bundledClasses || cachedInDim < bundledInDim || !hasWeights) {
                        f.writeText(bundledJson, Charsets.UTF_8)
                        return bundledJson
                    }
                    if (cachedClasses > bundledClasses || isCustomTrained) {
                        return cachedJson
                    }
                    // Stale shadow copy with same number of classes: overwrite from bundled APK
                    f.writeText(bundledJson, Charsets.UTF_8)
                    return bundledJson
                } catch (_: Exception) {
                    // Corrupted cached JSON: restore from bundled
                    f.writeText(bundledJson, Charsets.UTF_8)
                    return bundledJson
                }
            } else if (cachedJson != null) {
                return cachedJson
            }
        }

        if (bundledJson != null) {
            try {
                f.writeText(bundledJson, Charsets.UTF_8)
            } catch (_: Exception) {}
            return bundledJson
        }

        throw IllegalStateException("Model asset $filename not found in APK assets or local storage")
    }

    private fun downloadFile(urlStr: String, destination: File): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return false
            }

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            return destination.exists() && destination.length() > 0
        } catch (e: Exception) {
            destination.delete()
            return false
        } finally {
            connection?.disconnect()
        }
    }
}
