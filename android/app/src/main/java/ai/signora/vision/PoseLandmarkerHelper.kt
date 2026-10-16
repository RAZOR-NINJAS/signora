package ai.signora.vision

import android.content.Context
import android.util.Log
import ai.signora.filter.LandmarkSmoother
import ai.signora.models.Point3D
import ai.signora.models.PoseLandmarks
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance MediaPipe Tasks Vision wrapper for real-time live video body pose tracking.
 *
 * Configured with One-Euro temporal landmark smoothing, tuned confidence gates,
 * native ImageProcessingOptions hardware rotation, and automatic GPU acceleration with transparent CPU fallback.
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val modelFile: File? = null,
    private val onResults: (PoseLandmarks?, Long, Long) -> Unit,
    private val onError: (String) -> Unit = {}
) {

    data class FrameMeta(val frameId: Long, val isFrontCamera: Boolean)

    private var poseLandmarker: PoseLandmarker? = null
    var activeDelegateName: String = "CPU"
        private set

    // One-Euro smoother for 33 pose landmarks: steady shoulders/face with zero latency
    private val poseSmoother = LandmarkSmoother(maxPoints = 33, minCutoff = 1.0f, beta = 0.05f)
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
                onError("Failed to initialize MediaPipe PoseLandmarker with GPU and CPU.")
            }
        }

        Log.i("TRACKPERF", "PoseLandmarker initialized with delegate: $activeDelegateName")
    }

    private fun tryInit(delegate: Delegate): Boolean {
        return try {
            val baseOptionsBuilder = BaseOptions.builder()
            baseOptionsBuilder.setDelegate(delegate)

            var configured = false
            // 1. Prefer loading directly from APK assets
            try {
                baseOptionsBuilder.setModelAssetPath("models/pose_landmarker.task")
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
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(0.30f)
            .setMinTrackingConfidence(0.30f)
            .setMinPosePresenceConfidence(0.30f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: PoseLandmarkerResult, _ ->
                val timestamp = result.timestampMs()
                val meta = frameMetaMap.remove(timestamp) ?: FrameMeta(0L, true)
                processResult(result, meta.frameId, meta.isFrontCamera, timestamp)
            }
            .setErrorListener { error ->
                onError("MediaPipe pose tracking error: ${error.message}")
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
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
            poseLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTime)
        } catch (e: Exception) {
            frameMetaMap.remove(frameTime)
            onError("Pose detection frame drop: ${e.message}")
        }
    }

    private fun processResult(
        result: PoseLandmarkerResult,
        frameId: Long,
        isFrontCamera: Boolean,
        timestamp: Long
    ) {
        val allLandmarks = result.landmarks()
        if (allLandmarks.isEmpty() || allLandmarks[0].size < 25) {
            onResults(null, frameId, timestamp)
            return
        }

        val lms = allLandmarks[0]
        val rawPoints = lms.map { lm ->
            Point3D(lm.x(), lm.y(), lm.z())
        }

        val smoothedPoints = poseSmoother.filter(rawPoints, timestamp)
        val pose = PoseLandmarks(smoothedPoints, isFrontCamera)
        onResults(pose, frameId, timestamp)
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        poseSmoother.reset()
        frameMetaMap.clear()
    }
}
