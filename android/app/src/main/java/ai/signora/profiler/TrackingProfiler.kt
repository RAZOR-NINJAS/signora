package ai.signora.profiler

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stage-by-stage microsecond profiler for the vision and inference pipeline.
 *
 * Instruments:
 * 1. Camera frame arrival (t0)
 * 2. ImageProxy -> MediaPipe image conversion (t1)
 * 3. detectAsync call (t2)
 * 4. MediaPipe result callback (t3)
 * 5. Feature extraction (t4)
 * 6. Landmark smoothing (t5)
 * 7. MLP neural forward pass (t6)
 * 8. Vote and dwell debouncing (t7)
 * 9. UI state emission (t8)
 * 10. First draw of the overlay (t9)
 *
 * Computes average and p95 latencies and emits every 1000ms with logcat tag TRACKPERF.
 */
object TrackingProfiler {

    private const val TAG = "TRACKPERF"
    private const val WINDOW_SIZE = 120

    data class FrameTimestamps(
        val frameId: Long,
        var t0_arrivalNs: Long = 0L,
        var t1_convertedNs: Long = 0L,
        var t2_detectCallNs: Long = 0L,
        var t3_callbackNs: Long = 0L,
        var t4_featuresNs: Long = 0L,
        var t5_smoothingNs: Long = 0L,
        var t6_mlpNs: Long = 0L,
        var t7_voteDwellNs: Long = 0L,
        var t8_uiEmitNs: Long = 0L,
        var t9_drawNs: Long = 0L
    )

    private val frameMap = ConcurrentHashMap<Long, FrameTimestamps>()
    private val nextFrameId = AtomicInteger(1)

    // Windowed stage latencies in milliseconds
    private val conversionMsList = ArrayList<Float>(WINDOW_SIZE)
    private val mediapipeMsList = ArrayList<Float>(WINDOW_SIZE)
    private val featuresMsList = ArrayList<Float>(WINDOW_SIZE)
    private val smoothingMsList = ArrayList<Float>(WINDOW_SIZE)
    private val mlpMsList = ArrayList<Float>(WINDOW_SIZE)
    private val voteMsList = ArrayList<Float>(WINDOW_SIZE)
    private val uiEmitMsList = ArrayList<Float>(WINDOW_SIZE)
    private val drawMsList = ArrayList<Float>(WINDOW_SIZE)
    private val endToEndMsList = ArrayList<Float>(WINDOW_SIZE)

    private var lastLogTimeMs = SystemClock.elapsedRealtime()
    private var windowFrameCount = 0
    var activeDelegate: String = "Unknown"

    fun onFrameArrival(): Long {
        val id = nextFrameId.getAndIncrement().toLong()
        val ts = FrameTimestamps(
            frameId = id,
            t0_arrivalNs = SystemClock.elapsedRealtimeNanos()
        )
        frameMap[id] = ts
        return id
    }

    fun onConversionDone(frameId: Long) {
        frameMap[frameId]?.t1_convertedNs = SystemClock.elapsedRealtimeNanos()
    }

    fun onDetectAsyncCalled(frameId: Long) {
        frameMap[frameId]?.t2_detectCallNs = SystemClock.elapsedRealtimeNanos()
    }

    fun onResultCallback(frameId: Long) {
        frameMap[frameId]?.t3_callbackNs = SystemClock.elapsedRealtimeNanos()
    }

    fun onFeaturesExtracted(frameId: Long) {
        frameMap[frameId]?.t4_featuresNs = SystemClock.elapsedRealtimeNanos()
    }

    fun onSmoothingDone(frameId: Long) {
        frameMap[frameId]?.t5_smoothingNs = SystemClock.elapsedRealtimeNanos()
    }

    fun onMlpDone(frameId: Long) {
        frameMap[frameId]?.t6_mlpNs = SystemClock.elapsedRealtimeNanos()
    }

    fun onVoteDwellDone(frameId: Long) {
        frameMap[frameId]?.t7_voteDwellNs = SystemClock.elapsedRealtimeNanos()
    }

    fun onUiStateEmitted(frameId: Long) {
        val now = SystemClock.elapsedRealtimeNanos()
        val ts = frameMap[frameId] ?: return
        ts.t8_uiEmitNs = now
    }

    fun onOverlayDrawn(frameId: Long) {
        val now = SystemClock.elapsedRealtimeNanos()
        val ts = frameMap.remove(frameId) ?: return
        ts.t9_drawNs = now
        recordCompletedFrame(ts)
    }

    private fun recordCompletedFrame(ts: FrameTimestamps) {
        synchronized(this) {
            windowFrameCount++
            val nsToMs = 1_000_000f

            val conv = (ts.t1_convertedNs - ts.t0_arrivalNs).coerceAtLeast(0L) / nsToMs
            val mp = (ts.t3_callbackNs - ts.t2_detectCallNs).coerceAtLeast(0L) / nsToMs
            val feat = (ts.t4_featuresNs - ts.t3_callbackNs).coerceAtLeast(0L) / nsToMs
            val smooth = (ts.t5_smoothingNs - ts.t4_featuresNs).coerceAtLeast(0L) / nsToMs
            val mlp = (ts.t6_mlpNs - ts.t5_smoothingNs).coerceAtLeast(0L) / nsToMs
            val vote = (ts.t7_voteDwellNs - ts.t6_mlpNs).coerceAtLeast(0L) / nsToMs
            val ui = (ts.t8_uiEmitNs - ts.t7_voteDwellNs).coerceAtLeast(0L) / nsToMs
            val draw = (ts.t9_drawNs - ts.t8_uiEmitNs).coerceAtLeast(0L) / nsToMs
            val total = (ts.t9_drawNs - ts.t0_arrivalNs).coerceAtLeast(0L) / nsToMs

            conversionMsList.add(conv)
            mediapipeMsList.add(mp)
            featuresMsList.add(feat)
            smoothingMsList.add(smooth)
            mlpMsList.add(mlp)
            voteMsList.add(vote)
            uiEmitMsList.add(ui)
            drawMsList.add(draw)
            endToEndMsList.add(total)

            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastLogTimeMs >= 1000L) {
                val elapsedSec = (nowMs - lastLogTimeMs) / 1000f
                val fps = windowFrameCount / elapsedSec

                val e2eAvg = endToEndMsList.average().toFloat()
                val e2eP95 = percentile95(endToEndMsList)
                val convAvg = conversionMsList.average().toFloat()
                val mpAvg = mediapipeMsList.average().toFloat()
                val mpP95 = percentile95(mediapipeMsList)
                val featAvg = featuresMsList.average().toFloat()
                val smoothAvg = smoothingMsList.average().toFloat()
                val mlpAvg = mlpMsList.average().toFloat()
                val voteAvg = voteMsList.average().toFloat()
                val uiAvg = uiEmitMsList.average().toFloat()
                val drawAvg = drawMsList.average().toFloat()

                Log.i(
                    TAG,
                    String.format(
                        "TRACKPERF: FPS=%.1f | E2E avg=%.1fms (p95=%.1fms) | Conversion=%.1fms | MediaPipe=%.1fms (p95=%.1fms) | Features=%.2fms | Smooth=%.2fms | MLP=%.2fms | Vote=%.2fms | UI=%.2fms | Draw=%.2fms | Delegate=%s",
                        fps, e2eAvg, e2eP95, convAvg, mpAvg, mpP95, featAvg, smoothAvg, mlpAvg, voteAvg, uiAvg, drawAvg, activeDelegate
                    )
                )

                // Clear window
                conversionMsList.clear()
                mediapipeMsList.clear()
                featuresMsList.clear()
                smoothingMsList.clear()
                mlpMsList.clear()
                voteMsList.clear()
                uiEmitMsList.clear()
                drawMsList.clear()
                endToEndMsList.clear()
                windowFrameCount = 0
                lastLogTimeMs = nowMs

                // Prune stale frames older than 2 seconds
                if (frameMap.size > 200) {
                    val cutoff = SystemClock.elapsedRealtimeNanos() - 2_000_000_000L
                    frameMap.entries.removeIf { it.value.t0_arrivalNs < cutoff }
                }
            }
        }
    }

    private fun percentile95(list: List<Float>): Float {
        if (list.isEmpty()) return 0f
        val sorted = list.sorted()
        val index = ((sorted.size - 1) * 0.95f).toInt()
        return sorted[index]
    }
}
