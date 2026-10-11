package ai.signora.vision

import android.content.Context
import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.File

/**
 * High-performance MediaPipe Tasks Vision wrapper for real-time live video hand tracking.
 *
 * Configured with automatic GPU acceleration delegate and CPU fallback for low-power chipsets.
 */
class HandLandmarkerHelper(
    private val context: Context,
    private val modelFile: File?,
    private val onResults: (List<HandLandmarks>, Long) -> Unit,
    private val onError: (String) -> Unit
) {

    private var handLandmarker: HandLandmarker? = null

    init {
        setupLandmarker()
    }

    private fun setupLandmarker() {
        try {
            // Attempt GPU acceleration first, fallback to CPU
            val baseOptionsBuilder = BaseOptions.builder()
            if (modelFile != null && modelFile.exists()) {
                baseOptionsBuilder.setModelAssetPath(modelFile.absolutePath)
            } else {
                baseOptionsBuilder.setModelAssetPath("models/hand_landmarker.task")
            }

            try {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
                initWithOptions(baseOptionsBuilder.build())
            } catch (e: Exception) {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
                initWithOptions(baseOptionsBuilder.build())
            }
        } catch (e: Exception) {
            onError("MediaPipe initialization error: ${e.message}")
        }
    }

    private fun initWithOptions(baseOptions: BaseOptions) {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(2)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: HandLandmarkerResult, _ ->
                processResult(result, result.timestampMs())
            }
            .setErrorListener { error ->
                onError("MediaPipe tracking error: ${error.message}")
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detectLiveStream(mpImage: MPImage, frameTime: Long) {
        try {
            handLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            onError("Detection frame drop: ${e.message}")
        }
    }

    private fun processResult(result: HandLandmarkerResult, timestamp: Long) {
        val hands = mutableListOf<HandLandmarks>()
        val allLandmarks = result.landmarks()
        val allHandedness = result.handedness()

        for (i in allLandmarks.indices) {
            val lms = allLandmarks[i]
            val points = lms.map { lm ->
                Point3D(lm.x(), lm.y(), lm.z())
            }
            val isRight = if (i < allHandedness.size && allHandedness[i].isNotEmpty()) {
                allHandedness[i][0].categoryName() == "Right"
            } else {
                true
            }
            hands.add(HandLandmarks(points, isRight))
        }

        onResults(hands, timestamp)
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}
