package ai.signora.vision

import android.content.Context
import android.util.Log
import ai.signora.filter.LandmarkSmoother
import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import ai.signora.profiler.TrackingProfiler
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance MediaPipe Tasks Vision wrapper for real-time live video hand tracking.
 *
 * Configured with One-Euro temporal landmark smoothing, tuned confidence gates,
 * native ImageProcessingOptions hardware rotation, and automatic GPU acceleration with transparent CPU fallback.
 */
class HandLandmarkerHelper(
    private val context: Context,
    private val modelFile: File?,
    private val onResults: (List<HandLandmarks>, Long, Long) -> Unit,
    private val onError: (String) -> Unit
) {

    data class FrameMeta(val frameId: Long, val isFrontCamera: Boolean)

    private var handLandmarker: HandLandmarker? = null
    var activeDelegateName: String = "CPU"
        private set

    private class HandTrack(
        val smoother: LandmarkSmoother,
        var isRight: Boolean = true,
        var lastWrist: Point3D? = null,
        var lastSeenMs: Long = -1L
    ) {
        fun reset() {
            smoother.reset()
            lastWrist = null
            lastSeenMs = -1L
        }
    }

    // Spatial hand continuity tracks: beta = 3.5f for instant zero-lag response, minCutoff = 0.8f for rock-solid stability
    private val tracks = arrayOf(
        HandTrack(LandmarkSmoother(maxPoints = 21, minCutoff = 0.8f, beta = 3.5f), isRight = true),
        HandTrack(LandmarkSmoother(maxPoints = 21, minCutoff = 0.8f, beta = 3.5f), isRight = false)
    )

    private val frameMetaMap = ConcurrentHashMap<Long, FrameMeta>()

    init {
        setupLandmarker()
    }

    private fun setupLandmarker() {
        val gpuSuccess = tryInit(Delegate.GPU)
        if (gpuSuccess) {
            activeDelegateName = "GPU"
        } else {
            val cpuSuccess = tryInit(Delegate.CPU)
            if (cpuSuccess) {
                activeDelegateName = "CPU"
            } else {
                onError("Failed to initialize MediaPipe HandLandmarker with GPU and CPU.")
            }
        }

        TrackingProfiler.activeDelegate = activeDelegateName
        Log.i("TRACKPERF", "HandLandmarker initialized with delegate: $activeDelegateName")
    }

    private fun tryInit(delegate: Delegate): Boolean {
        return try {
            val baseOptionsBuilder = BaseOptions.builder()
            baseOptionsBuilder.setDelegate(delegate)

            var configured = false
            // 1. Only load from APK assets if file actually exists
            val assetExists = try {
                context.assets.open("models/hand_landmarker.task").use { true }
            } catch (_: Exception) {
                false
            }
            if (assetExists) {
                baseOptionsBuilder.setModelAssetPath("models/hand_landmarker.task")
                configured = true
            }

            // 2. If needed, load from external downloaded file buffer
            if (!configured && modelFile != null && modelFile.exists() && modelFile.length() > 0) {
                val bytes = modelFile.readBytes()
                baseOptionsBuilder.setModelAssetBuffer(java.nio.ByteBuffer.wrap(bytes))
                configured = true
            }

            if (configured) {
                initWithOptions(baseOptionsBuilder.build())
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            false
        }
    }

    private fun initWithOptions(baseOptions: BaseOptions) {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.50f)
            .setMinTrackingConfidence(0.50f)
            .setMinHandPresenceConfidence(0.55f)
            .setNumHands(2)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: HandLandmarkerResult, _ ->
                val timestamp = result.timestampMs()
                val meta = frameMetaMap.remove(timestamp) ?: FrameMeta(0L, true)
                TrackingProfiler.onResultCallback(meta.frameId)
                processResult(result, meta.frameId, meta.isFrontCamera, timestamp)
            }
            .setErrorListener { error ->
                onError("MediaPipe tracking error: ${error.message}")
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    private var lastInputTimestamp = -1L

    fun detectLiveStream(
        mpImage: MPImage,
        rotationDegrees: Int,
        isFrontCamera: Boolean,
        frameId: Long,
        frameTime: Long
    ) {
        var adjustedTime = frameTime
        try {
            adjustedTime = if (frameTime <= lastInputTimestamp) lastInputTimestamp + 1 else frameTime
            lastInputTimestamp = adjustedTime

            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            frameMetaMap[adjustedTime] = FrameMeta(frameId, isFrontCamera)
            handLandmarker?.detectAsync(mpImage, imageProcessingOptions, adjustedTime)
        } catch (e: Exception) {
            frameMetaMap.remove(adjustedTime)
            onError("Detection frame drop: ${e.message}")
        }
    }

    private fun processResult(
        result: HandLandmarkerResult,
        frameId: Long,
        isFrontCamera: Boolean,
        timestamp: Long
    ) {
        val hands = mutableListOf<HandLandmarks>()
        val allLandmarks = result.landmarks()
        val allHandedness = result.handedness()

        // Expire tracks that haven't received detections in > 250ms
        for (track in tracks) {
            if (track.lastSeenMs > 0L && (timestamp - track.lastSeenMs) > 250L) {
                track.reset()
            }
        }

        val numDetections = allLandmarks.size
        if (numDetections == 0) {
            TrackingProfiler.onSmoothingDone(frameId)
            onResults(emptyList(), frameId, timestamp)
            return
        }

        // Inline detection data (avoid per-frame inner class allocation)
        val rawPointsList = ArrayList<List<Point3D>>(numDetections)
        val wristList = ArrayList<Point3D>(numDetections)
        val isRightList = BooleanArray(numDetections)
        val scoreList = FloatArray(numDetections)

        for (i in 0 until numDetections) {
            val lms = allLandmarks[i]
            val pts = lms.map { lm -> Point3D(lm.x(), lm.y(), lm.z()) }
            val cat = if (i < allHandedness.size && allHandedness[i].isNotEmpty()) allHandedness[i][0] else null
            val isR = if (isFrontCamera) {
                cat?.categoryName() == "Right"
            } else {
                cat?.categoryName() == "Left" // Mirror swap for rear camera
            }
            rawPointsList.add(pts)
            wristList.add(pts[0])
            isRightList[i] = isR
            scoreList[i] = cat?.score() ?: 0.5f
        }

        fun distSq(a: Point3D, b: Point3D): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return dx * dx + dy * dy
        }

        val assignment = IntArray(numDetections) { -1 }
        val maxMatchDistSq = 0.35f * 0.35f

        if (numDetections == 1) {
            val wrist = wristList[0]
            val t0 = tracks[0]
            val t1 = tracks[1]
            val d0 = if (t0.lastWrist != null) distSq(wrist, t0.lastWrist!!) else Float.MAX_VALUE
            val d1 = if (t1.lastWrist != null) distSq(wrist, t1.lastWrist!!) else Float.MAX_VALUE

            val chosen = when {
                d0 < maxMatchDistSq && d0 <= d1 -> 0
                d1 < maxMatchDistSq && d1 < d0 -> 1
                t0.lastWrist != null && t1.lastWrist == null -> if (d0 < maxMatchDistSq || isRightList[0] == t0.isRight) 0 else 1
                t1.lastWrist != null && t0.lastWrist == null -> if (d1 < maxMatchDistSq || isRightList[0] == t1.isRight) 1 else 0
                else -> if (isRightList[0]) 0 else 1
            }
            assignment[0] = chosen
        } else if (numDetections >= 2) {
            val t0 = tracks[0]
            val t1 = tracks[1]
            val w0 = wristList[0]
            val w1 = wristList[1]

            val d00 = if (t0.lastWrist != null) distSq(w0, t0.lastWrist!!) else Float.MAX_VALUE
            val d11 = if (t1.lastWrist != null) distSq(w1, t1.lastWrist!!) else Float.MAX_VALUE
            val d01 = if (t1.lastWrist != null) distSq(w0, t1.lastWrist!!) else Float.MAX_VALUE
            val d10 = if (t0.lastWrist != null) distSq(w1, t0.lastWrist!!) else Float.MAX_VALUE

            if (t0.lastWrist != null && t1.lastWrist != null) {
                if ((d00 + d11) <= (d01 + d10)) {
                    assignment[0] = 0; assignment[1] = 1
                } else {
                    assignment[0] = 1; assignment[1] = 0
                }
            } else if (t0.lastWrist != null) {
                if (d00 <= d10) {
                    assignment[0] = 0; assignment[1] = 1
                } else {
                    assignment[0] = 1; assignment[1] = 0
                }
            } else if (t1.lastWrist != null) {
                if (d01 <= d11) {
                    assignment[0] = 1; assignment[1] = 0
                } else {
                    assignment[0] = 0; assignment[1] = 1
                }
            } else {
                if (isRightList[0] && !isRightList[1]) {
                    assignment[0] = 0; assignment[1] = 1
                } else if (!isRightList[0] && isRightList[1]) {
                    assignment[0] = 1; assignment[1] = 0
                } else {
                    // Mirrored selfie view: right hand appears on right of screen (higher X)
                    if (w0.x >= w1.x) {
                        assignment[0] = 0; assignment[1] = 1
                    } else {
                        assignment[0] = 1; assignment[1] = 0
                    }
                }
            }
        }

        for (i in 0 until numDetections) {
            val trackIdx = assignment[i].coerceIn(0, 1)
            val track = tracks[trackIdx]

            // Stabilize handedness: only update if track is new or confidence is high (> 0.70)
            if (track.lastWrist == null || scoreList[i] >= 0.70f) {
                track.isRight = isRightList[i]
            }

            val smoothed = track.smoother.filter(rawPointsList[i], timestamp)
            track.lastWrist = smoothed[0]
            track.lastSeenMs = timestamp

            hands.add(HandLandmarks(smoothed, track.isRight, isFrontCamera))
        }

        TrackingProfiler.onSmoothingDone(frameId)
        onResults(hands, frameId, timestamp)
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        for (track in tracks) {
            track.reset()
        }
        frameMetaMap.clear()
    }
}
