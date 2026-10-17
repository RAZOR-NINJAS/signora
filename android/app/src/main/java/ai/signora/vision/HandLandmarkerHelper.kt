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

    // Landmark smoothers tuned for zero lag: beta = 0.22f allows instant tracking without phase lag, minCutoff = 1.0f keeps still hands rock solid
    private val rightSmoother = LandmarkSmoother(minCutoff = 1.0f, beta = 0.22f)
    private val leftSmoother = LandmarkSmoother(minCutoff = 1.0f, beta = 0.22f)

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
            // 1. Prefer loading directly from APK assets
            try {
                baseOptionsBuilder.setModelAssetPath("models/hand_landmarker.task")
                configured = true
            } catch (_: Exception) {
                // Fallback below
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
        } catch (e: Exception) {
            false
        }
    }

    private fun initWithOptions(baseOptions: BaseOptions) {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.40f)
            .setMinTrackingConfidence(0.40f)
            .setMinHandPresenceConfidence(0.40f)
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

    fun detectLiveStream(
        mpImage: MPImage,
        rotationDegrees: Int,
        isFrontCamera: Boolean,
        frameId: Long,
        frameTime: Long
    ) {
        try {
            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            frameMetaMap[frameTime] = FrameMeta(frameId, isFrontCamera)
            handLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTime)
        } catch (e: Exception) {
            frameMetaMap.remove(frameTime)
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

        for (i in allLandmarks.indices) {
            val lms = allLandmarks[i]
            val rawPoints = lms.map { lm ->
                Point3D(lm.x(), lm.y(), lm.z())
            }

            val rawIsRight = if (i < allHandedness.size && allHandedness[i].isNotEmpty()) {
                allHandedness[i][0].categoryName() == "Right"
            } else {
                true
            }

            // MediaPipe correctly identifies anatomical handedness on the mirrored frame.
            val isRight = rawIsRight

            // Apply One-Euro filter to eliminate jitter while keeping instant zero-lag response
            val smoothedPoints = if (isRight) {
                rightSmoother.filter(rawPoints, timestamp)
            } else {
                leftSmoother.filter(rawPoints, timestamp)
            }

            hands.add(HandLandmarks(smoothedPoints, isRight, isFrontCamera))
        }

        TrackingProfiler.onSmoothingDone(frameId)
        onResults(hands, frameId, timestamp)
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        rightSmoother.reset()
        leftSmoother.reset()
        frameMetaMap.clear()
    }
}
