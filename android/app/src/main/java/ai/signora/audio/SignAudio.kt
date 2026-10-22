package ai.signora.audio

import android.content.Context
import android.media.MediaPlayer
import ai.signora.R

/**
 * Optional audio cue per sign, bundled in res/raw.
 * Signs without an entry simply have no playback button.
 */
object SignAudio {

    private val CUES: Map<String, Int> = mapOf(
        "HALA_MADRID" to R.raw.hala_madrid,
        "SUII" to R.raw.suii
    )

    private var player: MediaPlayer? = null

    fun hasCue(signName: String): Boolean = CUES.containsKey(signName.uppercase())

    fun play(context: Context, signName: String) {
        val resId = CUES[signName.uppercase()] ?: return
        stop()
        val mp = MediaPlayer.create(context.applicationContext, resId) ?: return
        mp.setOnCompletionListener {
            it.release()
            if (player === it) player = null
        }
        player = mp
        mp.start()
    }

    fun stop() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }
}
