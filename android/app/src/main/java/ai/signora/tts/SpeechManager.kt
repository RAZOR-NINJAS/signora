package ai.signora.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Native Android offline speech synthesis manager.
 *
 * Emits zero cloud network calls and leverages the built-in Android OS neural TTS engine.
 */
class SpeechManager(context: Context, onReady: (() -> Unit)? = null) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isMuted: Boolean = false
    var isSpeaking: Boolean = false
        private set

    private var isInitialized = false
    private val readyCallback = onReady

    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L

    companion object {
        private val PHONETIC_MAP = mapOf(
            // Alphabet fingerspelling
            "B" to "Bee", "C" to "See", "D" to "Dee", "E" to "Ee",
            "F" to "Eff", "G" to "Jee", "H" to "Aitch", "I" to "Eye", "J" to "Jay",
            "K" to "Kay", "L" to "Ell", "M" to "Emm", "N" to "Enn", "O" to "Oh",
            "P" to "Pee", "Q" to "Queue", "R" to "Arr", "S" to "Ess", "T" to "Tee",
            "U" to "You", "V" to "Vee", "W" to "Double you", "X" to "Ex", "Y" to "Why", "Z" to "Zed",
            // Digits
            "0" to "Zero", "1" to "One", "2" to "Two", "3" to "Three", "4" to "Four",
            "5" to "Five", "6" to "Six", "7" to "Seven", "8" to "Eight", "9" to "Nine",
            // Ordinals
            "1ST" to "First", "2ND" to "Second", "3RD" to "Third",
            // Special tokens
            "SPACE" to "Space", "DEL" to "Delete", "BACKSPACE" to "Delete",
            "ISL" to "Indian Sign Language", "ASL" to "American Sign Language",
            "SOS" to "S O S", "OK" to "Okay",
            "PLS" to "Please", "PLZ" to "Please",
            "THX" to "Thank you", "TY" to "Thank you",
            "DOC" to "Doctor", "DR" to "Doctor",
            "HOSP" to "Hospital", "MED" to "Medicine", "MEDS" to "Medicine",
            "EMRG" to "Emergency", "EMERG" to "Emergency",
            "AMB" to "Ambulance", "POL" to "Police", "WTR" to "Water",
            "BATH" to "Washroom", "WASH" to "Washroom"
        )

        fun normalizeText(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val upper = trimmed.uppercase()
            PHONETIC_MAP[upper]?.let { return it }

            var cleaned = if (trimmed.contains("_")) trimmed.replace("_", " ") else trimmed
            val cleanUpper = cleaned.uppercase()
            PHONETIC_MAP[cleanUpper]?.let { return it }

            return cleaned
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = tts?.setLanguage(Locale.US)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            configureVoice()
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }
            })
            isInitialized = true
            readyCallback?.invoke()
        }
    }

    /**
     * Prefer the highest-quality installed voice for the current locale.
     * Engines often default to a low-quality variant even when a better local
     * voice is present; this is the cheapest way to sound less synthetic.
     */
    private fun configureVoice() {
        val engine = tts ?: return
        try {
            val target = engine.voice?.locale ?: Locale.US
            val candidates = engine.voices
                ?.filter { it.locale.language == target.language && it.locale.country == target.country }
                ?.filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                .orEmpty()
            if (candidates.isEmpty()) return
            val best = candidates
                .filter { !it.isNetworkConnectionRequired }
                .maxByOrNull { it.quality }
                ?: candidates.maxByOrNull { it.quality }
            if (best != null) engine.voice = best
        } catch (_: Exception) {
            // Keep whatever voice the engine picked
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun speak(text: String, interrupt: Boolean = true) {
        if (isMuted || !isInitialized || text.isBlank()) return

        val trimmed = text.trim()
        val now = System.currentTimeMillis()

        // Do not interrupt with the identical utterance if currently speaking
        if (trimmed == lastSpokenText && isSpeaking) {
            return
        }

        // Debounce identical rapid triggers (< 300ms)
        if (trimmed == lastSpokenText && (now - lastSpokenTime) < 300L) {
            return
        }

        lastSpokenText = trimmed
        lastSpokenTime = now

        val normalized = normalizeText(trimmed)
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(normalized, queueMode, null, "signora_tts_$now")
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isSpeaking = false
    }
}
