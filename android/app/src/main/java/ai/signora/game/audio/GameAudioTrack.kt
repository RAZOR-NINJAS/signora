package ai.signora.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Signora procedural 8-bit / synth audio engine.
 *
 * Every sound effect and the fever backing loop are synthesized directly into
 * 16-bit PCM buffers in pure Kotlin and played back through [AudioTrack]. No
 * MP3/WAV assets are required. All AudioTrack creation, buffering and playback
 * is serialized onto a single dedicated background thread so the 60/120 FPS
 * Compose render loop is never blocked.
 */
class GameAudioTrack {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val TAU = (2.0 * PI).toFloat()

        // Musical note helper (A4 = 440Hz, equal temperament).
        private fun note(semitonesFromA4: Int): Float =
            440f * Math.pow(2.0, semitonesFromA4 / 12.0).toFloat()
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SignoraGameAudio").apply { priority = Thread.MAX_PRIORITY }
    }

    private val tracks = ArrayList<AudioTrack>(8)

    private val jumpTrack: AudioTrack
    private val duckTrack: AudioTrack
    private val milestoneTrack: AudioTrack
    private val signTrack: AudioTrack
    private val streakTrack: AudioTrack
    private val crashTrack: AudioTrack
    private val feverTrack: AudioTrack

    @Volatile private var released = false
    @Volatile private var feverRunning = false

    init {
        jumpTrack = createStaticTrack(synthJump())
        duckTrack = createStaticTrack(synthDuck())
        milestoneTrack = createStaticTrack(synthMilestone())
        signTrack = createStaticTrack(synthSignBonus())
        streakTrack = createStaticTrack(synthStreak())
        crashTrack = createStaticTrack(synthCrash())
        feverTrack = createStaticTrack(synthFeverLoop(), loop = true)
    }

    // ---------------------------------------------------------------------
    // Public controls
    // ---------------------------------------------------------------------

    fun jump() = playOnce(jumpTrack)
    fun duck() = playOnce(duckTrack)
    fun milestone() = playOnce(milestoneTrack)
    fun signBonus() = playOnce(signTrack)
    fun streak() = playOnce(streakTrack)
    fun crash() = playOnce(crashTrack)

    fun startFever() {
        if (released) return
        feverRunning = true
        submit {
            try {
                if (feverTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    feverTrack.reloadStaticData()
                    feverTrack.play()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun stopFever() {
        if (!feverRunning) return
        feverRunning = false
        submit {
            try {
                if (feverTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    feverTrack.stop()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        feverRunning = false
        submit {
            for (track in tracks) {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                    track.release()
                } catch (_: Throwable) {
                }
            }
            tracks.clear()
        }
        executor.shutdown()
    }

    // ---------------------------------------------------------------------
    // Playback core
    // ---------------------------------------------------------------------

    /** Submit a task, silently dropping it if the executor has already shut down. */
    private fun submit(block: Runnable) {
        try {
            executor.execute(block)
        } catch (_: Throwable) {
        }
    }

    private fun playOnce(track: AudioTrack) {
        if (released) return
        submit {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.setPlaybackHeadPosition(0)
                track.play()
            } catch (_: Throwable) {
                // Retry with a full static reload if the head reset is rejected.
                try {
                    track.reloadStaticData()
                    track.play()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun createStaticTrack(samples: ShortArray, loop: Boolean = false): AudioTrack {
        val sizeBytes = samples.size * 2
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(sizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        if (loop) track.setLoopPoints(0, samples.size, -1)
        tracks.add(track)
        return track
    }

    // ---------------------------------------------------------------------
    // Synthesis primitives
    // ---------------------------------------------------------------------

    private inline fun build(
        durationSec: Float,
        fadeIn: Boolean = true,
        fadeOut: Boolean = true,
        gen: (t: Float, i: Int) -> Float
    ): ShortArray {
        val n = (SAMPLE_RATE * durationSec).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        val fadeLen = (SAMPLE_RATE * 0.006f).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toFloat()
            var v = gen(t, i)
            if (fadeIn && i < fadeLen) v *= i / fadeLen.toFloat()
            if (fadeOut && i >= n - fadeLen) v *= (n - i) / fadeLen.toFloat()
            v = v.coerceIn(-1f, 1f)
            out[i] = (v * 32767f).toInt().toShort()
        }
        return out
    }

    private fun square(phase: Float): Float = if (sin(phase) >= 0f) 1f else -1f
    private fun saw(phase: Float): Float {
        val p = ((phase / TAU) % 1f + 1f) % 1f
        return 2f * p - 1f
    }

    // ---------------------------------------------------------------------
    // Sound effect designs
    // ---------------------------------------------------------------------

    /** Crisp upward blip: 320 Hz -> 880 Hz with instant decay. */
    private fun synthJump(): ShortArray {
        val dur = 0.16f
        var phase = 0f
        return build(dur) { t, _ ->
            val frac = (t / dur).coerceIn(0f, 1f)
            val freq = 320f + (880f - 320f) * frac
            phase += TAU * freq / SAMPLE_RATE
            val env = exp(-t * 22f)
            (0.72f * sin(phase) + 0.28f * square(phase)) * env * 0.85f
        }
    }

    /** Fast descending whoosh 420 Hz -> 160 Hz with airy noise. */
    private fun synthDuck(): ShortArray {
        val dur = 0.19f
        var phase = 0f
        return build(dur) { t, i ->
            val frac = (t / dur).coerceIn(0f, 1f)
            val freq = 420f - (420f - 160f) * frac
            phase += TAU * freq / SAMPLE_RATE
            val env = exp(-t * 14f)
            val noise = (Random.nextInt(-1000, 1000) / 1000f) * 0.35f
            (0.55f * sin(phase) + noise) * env * 0.8f
        }
    }

    /** Two-tone resonant bell: E6 -> B6. */
    private fun synthMilestone(): ShortArray {
        val dur = 0.42f
        val e6 = 1318.51f
        val b6 = 1975.53f
        return build(dur) { t, _ ->
            val (f, localT) = if (t < 0.14f) e6 to t else b6 to (t - 0.12f)
            val env = exp(-localT * 6.5f)
            val body = sin(TAU * f * t)
            val shimmer = 0.35f * sin(TAU * f * 2f * t)
            (body + shimmer) * env * 0.5f
        }
    }

    /** Bright sparkle arpeggio C-E-G-C (up one octave for a "magic" reward). */
    private fun synthSignBonus(): ShortArray {
        val dur = 0.46f
        val notes = floatArrayOf(
            note(-9),  // C5 ~ 523.25
            note(-5),  // E5 ~ 659.25
            note(-1),  // G5 ~ 783.99
            note(3)    // C6 ~ 1046.5
        )
        val step = 0.085f
        return build(dur) { t, _ ->
            val idx = (t / step).toInt().coerceIn(0, notes.size - 1)
            val localT = t - idx * step
            var v = 0f
            for (k in 0..idx) {
                val lt = t - k * step
                if (lt < 0f) continue
                val env = exp(-lt * 9f)
                v += sin(TAU * notes[k] * t) * env
            }
            val bell = 0.25f * sin(TAU * notes[idx] * 2f * t) * exp(-localT * 12f)
            (v * 0.42f + bell) * 0.85f
        }
    }

    /** Ascending pitch bell used on consecutive dodge streaks. */
    private fun synthStreak(): ShortArray {
        val dur = 0.22f
        var phase = 0f
        return build(dur) { t, _ ->
            val frac = (t / dur).coerceIn(0f, 1f)
            val freq = 660f + 660f * frac
            phase += TAU * freq / SAMPLE_RATE
            val env = exp(-t * 12f)
            (sin(phase) + 0.3f * sin(phase * 2f)) * env * 0.5f
        }
    }

    /** Shaped white-noise burst layered with a punchy 80 Hz kick. */
    private fun synthCrash(): ShortArray {
        val dur = 0.55f
        var phase = 0f
        return build(dur) { t, _ ->
            phase += TAU * 80f / SAMPLE_RATE
            val kick = sin(phase) * exp(-t * 16f) * 0.9f
            val noiseEnv = exp(-t * 9f)
            val noise = (Random.nextInt(-1000, 1000) / 1000f) * noiseEnv * 0.55f
            val ring = sin(TAU * 140f * t) * exp(-t * 5f) * 0.25f
            (kick + noise + ring) * 0.9f
        }
    }

    /**
     * Upbeat 130 BPM retro bassline / arpeggio loop (~1.85s, 4 beats).
     * Root progression A2 -> C3 -> E3 -> G3 with a driving 16th arpeggio.
     */
    private fun synthFeverLoop(): ShortArray {
        val beat = 60f / 130f
        val dur = beat * 4f
        // A minor-ish driving progression, semitones from A4 = -12 -> A3.
        val bassRoots = intArrayOf(-24, -21, -17, -14) // A2, C3, E3, G3
        val arpBase = intArrayOf(-12, -9, -5, -1)      // A3, C3, E4, G4ish
        return build(dur, fadeIn = true, fadeOut = true) { t, _ ->
            val beatIdx = (t / beat).toInt().coerceIn(0, bassRoots.size - 1)
            val beatT = t - beatIdx * beat

            // Driving bass: square wave on the beat with a gated envelope.
            val bassFreq = note(bassRoots[beatIdx])
            val bassEnv = exp(-beatT * 3.2f)
            val bass = square(TAU * bassFreq * t) * bassEnv * 0.34f

            // 16th note arpeggio that flickers through the chord tones.
            val stepIdx = (t / (beat / 4f)).toInt()
            val arpNote = arpBase[stepIdx % arpBase.size] + (if (stepIdx % 8 >= 4) 12 else 0)
            val arpT = t - stepIdx * (beat / 4f)
            val arpEnv = exp(-arpT * 11f)
            val arp = (sin(TAU * note(arpNote) * t) + 0.3f * square(TAU * note(arpNote) * t)) * arpEnv * 0.24f

            // Kick thump on every beat.
            val kickFreq = 60f + 90f * exp(-beatT * 24f)
            val kick = sin(TAU * kickFreq * t) * exp(-beatT * 20f) * 0.30f

            (bass + arp + kick) * 0.85f
        }
    }
}
