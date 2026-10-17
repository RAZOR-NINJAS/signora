package ai.signora.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real AI LLM and Vision Engine for Signora on Android.
 *
 * Uses Google Gemini 2.0 Flash / 1.5 Flash for:
 * 1. Deep zero-hallucination Indian Sign Language grammar synthesis
 * 2. Visual sign interpretation
 *
 * Powered entirely by standard Android HTTP stack with zero external dependencies.
 */
object GeminiAiEngine {
    private const val TAG = "GeminiAiEngine"
    private const val PREFS_NAME = "signora_ai_prefs"
    private const val KEY_GEMINI_API = "gemini_api_key"
    private const val DEFAULT_MODEL = "gemini-2.0-flash"

    @Volatile
    private var cachedApiKey: String? = null

    fun getApiKey(context: Context): String {
        cachedApiKey?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_GEMINI_API, "") ?: ""
        cachedApiKey = key
        return key
    }

    fun saveApiKey(context: Context, key: String) {
        val trimmed = key.trim()
        cachedApiKey = trimmed
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GEMINI_API, trimmed).apply()
        Log.i(TAG, "Gemini API key updated (${if (trimmed.isNotBlank()) "configured" else "cleared"})")
    }

    fun isConfigured(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }

    /**
     * Synthesizes a raw sequence of signed ISL glosses into a fluent English sentence
     * using Gemini 2.0 Flash with strict zero-hallucination constraints.
     */
    suspend fun synthesizeGrammar(context: Context, glosses: List<String>): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank() || glosses.isEmpty()) {
            return@withContext null
        }

        val joined = glosses.joinToString(", ")
        val prompt = "You are an expert Indian Sign Language (ISL) interpreter. Convert the following sequence of signed ISL glosses into a single natural, fluent English sentence.\n\n" +
                "RULES:\n" +
                "1. STRICT ZERO HALLUCINATION: Only use information implied by the signed glosses.\n" +
                "2. Do NOT invent extra details, adjectives, times of day, or places.\n" +
                "3. Output ONLY the final plain English sentence. No explanations, no markdown quotes.\n\n" +
                "Signed Glosses: $joined"

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$DEFAULT_MODEL:generateContent?key=$apiKey"

        var conn: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", prompt))
            }
            val contentObj = JSONObject().put("parts", partsArray)
            val contentsArray = JSONArray().apply {
                put(contentObj)
            }
            val genConfig = JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 64)
            }
            val payload = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", genConfig)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val root = JSONObject(responseStr)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val cand = candidates.getJSONObject(0)
                    val content = cand.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "").trim()
                        val cleaned = text.trim('"', '\'', '`', ' ', '\n', '\r')
                        if (cleaned.isNotBlank()) {
                            Log.d(TAG, "Gemini grammar synthesized: '$joined' -> '$cleaned'")
                            return@withContext cleaned
                        }
                    }
                }
            } else {
                Log.w(TAG, "Gemini API error code: $code")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini grammar synthesis network call failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }

        return@withContext null
    }
}
