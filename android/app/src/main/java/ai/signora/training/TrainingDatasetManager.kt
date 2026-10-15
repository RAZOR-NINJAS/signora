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

/**
 * High-performance on-device dataset manager for recording, saving, and exporting
 * 220D ISL feature sample datasets from Android phones.
 */
class TrainingDatasetManager(private val context: Context) {

    private val samplesDir: File = File(context.filesDir, "isl_samples").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Save/append a batch of 220D feature samples for a given sign.
     */
    fun saveSamples(sign: String, newSamples: List<FloatArray>): Int {
        val cleanSign = sign.uppercase().trim()
        val file = File(samplesDir, "$cleanSign.json")

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

        val root = JSONObject().apply {
            put("sign", cleanSign)
            put("sample_count", existingList.size)
            put("updated_at", System.currentTimeMillis())

            val samplesArray = JSONArray()
            for (s in existingList) {
                val sArr = JSONArray()
                for (v in s) {
                    sArr.put(v.toDouble())
                }
                samplesArray.put(sArr)
            }
            put("samples", samplesArray)
        }

        file.writeText(root.toString(), StandardCharsets.UTF_8)
        return existingList.size
    }

    /**
     * Returns a map of sign names to recorded sample count.
     */
    fun getRecordedCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val files = samplesDir.listFiles { f -> f.extension == "json" } ?: return counts
        for (f in files) {
            val signName = f.nameWithoutExtension.uppercase()
            try {
                val jsonStr = f.readText(StandardCharsets.UTF_8)
                val obj = JSONObject(jsonStr)
                val count = obj.optInt("sample_count", 0)
                counts[signName] = count
            } catch (e: Exception) {
                counts[signName] = 0
            }
        }
        return counts
    }

    /**
     * Clear recorded samples for a specific sign.
     */
    fun clearSignSamples(sign: String): Boolean {
        val file = File(samplesDir, "${sign.uppercase().trim()}.json")
        return if (file.exists()) file.delete() else true
    }

    /**
     * Assemble all recorded signs into a single structured dataset JSON file
     * and copy to public Downloads for easy transfer to PC.
     */
    fun exportCompleteDataset(): File? {
        val files = samplesDir.listFiles { f -> f.extension == "json" } ?: return null
        if (files.isEmpty()) return null

        val classesObj = JSONObject()
        var totalSamples = 0
        var totalClasses = 0

        for (f in files) {
            val signName = f.nameWithoutExtension.uppercase()
            try {
                val jsonStr = f.readText(StandardCharsets.UTF_8)
                val obj = JSONObject(jsonStr)
                val samplesArr = obj.optJSONArray("samples")
                if (samplesArr != null && samplesArr.length() > 0) {
                    classesObj.put(signName, samplesArr)
                    totalSamples += samplesArr.length()
                    totalClasses++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (totalClasses == 0) return null

        val datasetRoot = JSONObject().apply {
            put("format", "signora_isl_dataset")
            put("version", 1)
            put("device", "Android")
            put("feature_dim", 220)
            put("total_classes", totalClasses)
            put("total_samples", totalSamples)
            put("exported_at", System.currentTimeMillis())
            put("classes", classesObj)
        }

        val jsonOutput = datasetRoot.toString()

        // 1. Save to cache directory for FileProvider sharing
        val cacheFile = File(context.cacheDir, "signora_isl_dataset.json")
        cacheFile.writeText(jsonOutput, StandardCharsets.UTF_8)

        // 2. Also save to public Downloads so user can find it in their file manager
        saveToPublicDownloads(jsonOutput, "signora_isl_dataset.json")

        return cacheFile
    }

    private fun saveToPublicDownloads(jsonContent: String, filename: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                            writer.write(jsonContent)
                        }
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val targetFile = File(downloadsDir, filename)
                    targetFile.writeText(jsonContent, StandardCharsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Launch Android native Sharesheet so user can send dataset via Telegram, Drive, WhatsApp, etc.
     */
    fun shareDatasetFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
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
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing dataset: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
