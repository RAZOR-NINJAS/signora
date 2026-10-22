package ai.signora.training

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance on-device dataset manager for recording, saving, and exporting
 * 220D ISL feature sample datasets from Android phones.
 *
 * Uses cached manifest counts and direct stream serialization for sub-millisecond
 * metadata queries and zero-freeze storage persistence.
 */
class TrainingDatasetManager(
    private val context: Context? = null,
    baseDir: File? = null
) {

    private val samplesDir: File = baseDir ?: File(context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "isl_samples"), "isl_samples").apply {
        if (!exists()) mkdirs()
    }

    private val countsCache = ConcurrentHashMap<String, Int>()

    init {
        loadCountsCache()
    }

    private fun loadCountsCache() {
        countsCache.clear()
        val manifestFile = File(samplesDir, MANIFEST_FILENAME)
        if (manifestFile.exists()) {
            try {
                val jsonStr = manifestFile.readText(StandardCharsets.UTF_8)
                val matches = MANIFEST_ENTRY_REGEX.findAll(jsonStr)
                for (m in matches) {
                    val sign = m.groupValues[1].uppercase()
                    val count = m.groupValues[2].toInt()
                    countsCache[sign] = count
                }
                if (countsCache.isNotEmpty()) return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Manifest missing or corrupted: rebuild by scanning json files
        rebuildCountsCache()
    }

    private fun rebuildCountsCache() {
        val files = samplesDir.listFiles { f ->
            f.extension == "json" && f.name != MANIFEST_FILENAME
        } ?: return

        for (f in files) {
            val signName = f.nameWithoutExtension.uppercase()
            try {
                val count = readSampleCountFromFile(f)
                countsCache[signName] = count
            } catch (e: Exception) {
                countsCache[signName] = 0
            }
        }
        persistManifest()
    }

    private fun readSampleCountFromFile(file: File): Int {
        // Fast header scan without parsing full multi-megabyte sample array
        return try {
            file.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val header = CharArray(1024)
                val len = reader.read(header)
                if (len > 0) {
                    val headerStr = String(header, 0, len)
                    val match = SAMPLE_COUNT_REGEX.find(headerStr)
                    if (match != null) {
                        return match.groupValues[1].toInt()
                    }
                }
            }
            // Fallback to full JSON parse if header match failed
            val jsonStr = file.readText(StandardCharsets.UTF_8)
            val obj = JSONObject(jsonStr)
            obj.optInt("sample_count", 0)
        } catch (_: Exception) {
            0
        }
    }

    private fun persistManifest() {
        try {
            val manifestFile = File(samplesDir, MANIFEST_FILENAME)
            manifestFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write("{")
                var idx = 0
                for ((sign, count) in countsCache) {
                    if (idx > 0) writer.write(",")
                    writer.write("\"$sign\":$count")
                    idx++
                }
                writer.write("}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Save/append a batch of feature samples for a given sign.
     * Uses streamed serialization to a temporary file, then atomically renames.
     * Supports mode differentiation: isMotion = true creates a distinct MOTION dataset file.
     */
    @Synchronized
    fun saveSamples(sign: String, newSamples: List<FloatArray>, isMotion: Boolean = false): Int {
        val baseSign = sign.uppercase().trim()
        val key = if (isMotion) "${baseSign}_MOTION" else baseSign
        val file = File(samplesDir, "$key.json")
        val tmpFile = File(samplesDir, "$key.tmp")

        val existingList = mutableListOf<FloatArray>()
        if (file.exists()) {
            try {
                val jsonStr = file.readText(StandardCharsets.UTF_8)
                val obj = JSONObject(jsonStr)
                val arr = obj.optJSONArray("samples") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val sampleArr = arr.getJSONArray(i)
                    val sample = FloatArray(sampleArr.length()) { j ->
                        sampleArr.getDouble(j).toFloat()
                    }
                    existingList.add(sample)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        existingList.addAll(newSamples)
        val cappedList = if (existingList.size > MAX_SAMPLES_PER_SIGN) existingList.take(MAX_SAMPLES_PER_SIGN) else existingList
        val totalCount = cappedList.size

        // Direct buffered stream writing avoids massive JSONObject/JSONArray heap allocations
        tmpFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write("{\"sign\":\"")
            writer.write(baseSign)
            writer.write("\",\"mode\":\"")
            writer.write(if (isMotion) "motion" else "static")
            writer.write("\",\"sample_count\":")
            writer.write(totalCount.toString())
            writer.write(",\"updated_at\":")
            writer.write(System.currentTimeMillis().toString())
            writer.write(",\"samples\":[")
            for (i in cappedList.indices) {
                if (i > 0) writer.write(",")
                writer.write("[")
                val sample = cappedList[i]
                for (j in sample.indices) {
                    if (j > 0) writer.write(",")
                    val rounded = kotlin.math.round(sample[j] * 100000.0f) / 100000.0f
                    writer.write(rounded.toString())
                }
                writer.write("]")
            }
            writer.write("]}")
        }

        if (file.exists()) file.delete()
        tmpFile.renameTo(file)

        countsCache[key] = totalCount
        persistManifest()

        return totalCount
    }

    /**
     * Returns a map of sign names (or keys) to recorded sample count.
     * Instantaneous (0 ms) lookup via memory cache backed by manifest.
     */
    fun getRecordedCounts(): Map<String, Int> {
        return countsCache.toMap()
    }

    /**
     * Clear recorded samples for a specific sign and mode.
     */
    @Synchronized
    fun clearSignSamples(sign: String, isMotion: Boolean = false): Boolean {
        val baseSign = sign.uppercase().trim()
        val key = if (isMotion) "${baseSign}_MOTION" else baseSign
        val file = File(samplesDir, "$key.json")
        val tmp = File(samplesDir, "$key.tmp")
        if (tmp.exists()) tmp.delete()
        val deleted = if (file.exists()) file.delete() else true
        countsCache.remove(key)
        persistManifest()
        return deleted
    }

    /**
     * Clear all recorded samples across all signs and reset the dataset directory.
     */
    @Synchronized
    fun clearAllSamples(): Boolean {
        val files = samplesDir.listFiles() ?: return true
        for (f in files) {
            try {
                f.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        countsCache.clear()
        persistManifest()
        return true
    }

    /**
     * Load all recorded samples in memory for on-device training.
     */
    fun loadAllSamples(): Map<String, List<FloatArray>> {
        val dataset = mutableMapOf<String, List<FloatArray>>()
        val files = samplesDir.listFiles { f ->
            f.extension == "json" && f.name != MANIFEST_FILENAME
        } ?: return dataset

        for (f in files) {
            val signName = f.nameWithoutExtension.uppercase()
            try {
                val jsonStr = f.readText(StandardCharsets.UTF_8)
                val obj = JSONObject(jsonStr)
                val arr = obj.optJSONArray("samples") ?: continue
                val list = ArrayList<FloatArray>(arr.length())
                for (i in 0 until arr.length()) {
                    val sArr = arr.getJSONArray(i)
                    val sample = FloatArray(sArr.length()) { j -> sArr.getDouble(j).toFloat() }
                    list.add(sample)
                }
                if (list.isNotEmpty()) {
                    dataset[signName] = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return dataset
    }

    /**
     * Total count of all recorded samples across all signs.
     */
    fun getTotalSampleCount(): Int {
        return countsCache.values.sum()
    }

    /**
     * Assemble all recorded signs into a single structured dataset JSON file
     * and copy to public Downloads for easy transfer to PC.
     */
    fun exportCompleteDataset(): File? {
        val files = samplesDir.listFiles { f ->
            f.extension == "json" && f.name != MANIFEST_FILENAME
        } ?: return null
        if (files.isEmpty()) return null

        val cacheDir = context?.cacheDir ?: File(samplesDir, "cache").apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, "signora_isl_dataset.json")
        var totalSamples = 0
        var totalClasses = 0

        try {
            // Stream JSON directly to file to prevent huge heap allocations
            cacheFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write("{\"format\":\"signora_isl_dataset\",\"version\":1,\"device\":\"Android\",\"feature_dim\":220,")
                writer.write("\"exported_at\":${System.currentTimeMillis()},\"classes\":{")

                var classIdx = 0
                for (f in files) {
                    val signName = f.nameWithoutExtension.uppercase()
                    try {
                        val text = f.readText(StandardCharsets.UTF_8)
                        val sampleCount = readSampleCountFromFile(f)
                        if (sampleCount <= 0) continue

                        // Fast extraction of raw "[ ... ]" array directly from '"samples":['
                        // to avoid building millions of Double/JSONObject instances in memory
                        val samplesKey = "\"samples\":["
                        val samplesIdx = text.indexOf(samplesKey)
                        if (samplesIdx != -1) {
                            val arrayStart = samplesIdx + "\"samples\":".length
                            val arrayEnd = text.lastIndexOf(']')
                            if (arrayEnd > arrayStart) {
                                val rawSamplesArray = text.substring(arrayStart, arrayEnd + 1)
                                if (classIdx > 0) writer.write(",")
                                writer.write("\"$signName\":")
                                writer.write(rawSamplesArray)
                                totalSamples += sampleCount
                                totalClasses++
                                classIdx++
                                continue
                            }
                        }

                        // Robust fallback in case file structure has whitespace
                        val obj = JSONObject(text)
                        val samplesArr = obj.optJSONArray("samples")
                        if (samplesArr != null && samplesArr.length() > 0) {
                            if (classIdx > 0) writer.write(",")
                            writer.write("\"$signName\":${samplesArr}")
                            totalSamples += samplesArr.length()
                            totalClasses++
                            classIdx++
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                writer.write("},\"total_classes\":$totalClasses,\"total_samples\":$totalSamples}")
            }

            if (totalClasses == 0) {
                cacheFile.delete()
                return null
            }

            // Stream copy directly to public Downloads (no intermediate String allocation)
            saveToPublicDownloads(cacheFile, "signora_isl_dataset.json")

            return cacheFile
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    private fun saveToPublicDownloads(sourceFile: File, filename: String) {
        val ctx = context ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Delete previous entry with identical name in Downloads to prevent collision
                try {
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                    val selectionArgs = arrayOf(filename)
                    ctx.contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, selectionArgs)
                } catch (_: Throwable) {}

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(os)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, contentValues, null, null)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val targetFile = File(downloadsDir, filename)
                    sourceFile.copyTo(targetFile, overwrite = true)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Launch Android native Sharesheet so user can send dataset via Telegram, Drive, WhatsApp, etc.
     */
    fun shareDatasetFile(file: File) {
        val ctx = context ?: return
        try {
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Signora ISL Training Dataset (${file.length() / 1024} KB)")
                putExtra(Intent.EXTRA_TEXT, "Here is my recorded Signora ISL training dataset from Android. Ready for model training on PC!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share ISL Training Dataset").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(ctx, "Error sharing dataset: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val MAX_SAMPLES_PER_SIGN = 10000
        const val BATCH_SAMPLE_SIZE = 1000
        private const val MANIFEST_FILENAME = "manifest_counts.json"
        private val SAMPLE_COUNT_REGEX = Regex("\"sample_count\"\\s*:\\s*(\\d+)")
        private val MANIFEST_ENTRY_REGEX = Regex("\"([^\"]+)\"\\s*:\\s*(\\d+)")
    }
}
