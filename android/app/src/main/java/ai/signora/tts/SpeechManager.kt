package ai.signora.tts

import android.content.Context
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.05f)
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

    fun speak(text: String) {
        if (isMuted || !isInitialized || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "signora_tts_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
