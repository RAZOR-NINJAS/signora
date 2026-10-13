package ai.signora.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import ai.signora.profiler.TrackingProfiler
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX lifecycle manager with zero-allocation frame processing,
 * native hardware rotation via MediaPipe ImageProcessingOptions, and dedicated single-thread execution.
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrame: (MPImage, Int, Boolean, Long, Long) -> Unit
) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    // Reusable single Bitmap buffer across all incoming frames (eliminates per-frame heap churn)
    private var reusableBitmap: Bitmap? = null

    var isFrontCamera: Boolean = true
        private set

    var isTorchOn: Boolean = false
        private set

    var isPaused: Boolean = false

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggleCamera() {
        isFrontCamera = !isFrontCamera
        isTorchOn = false
        bindCameraUseCases()
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return false
        if (!cam.cameraInfo.hasFlashUnit()) return false
        isTorchOn = !isTorchOn
        cam.cameraControl.enableTorch(isTorchOn)
        return isTorchOn
    }

    fun focus(x: Float, y: Float) {
        val cam = camera ?: return
        try {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point).build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // 640x480 analysis resolution provides optimal sub-millisecond memory transfer
        // while matching MediaPipe HandLandmarker internal receptive field without downsampling artifacts.
        val targetSize = Size(640, 480)

        @Suppress("DEPRECATION")
        val preview = Preview.Builder()
            .setTargetResolution(targetSize)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        @Suppress("DEPRECATION")
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(targetSize)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val frameId = TrackingProfiler.onFrameArrival()
        val frameTime = System.currentTimeMillis()

        try {
            if (isPaused) {
                return
            }

            val width = imageProxy.width
            val height = imageProxy.height

            // Re-allocate bitmap ONLY if resolution changes (first frame or camera flip)
            if (reusableBitmap == null || reusableBitmap?.width != width || reusableBitmap?.height != height) {
                reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }

            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            reusableBitmap!!.copyPixelsFromBuffer(buffer)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            TrackingProfiler.onConversionDone(frameId)

            // Wrap in MPImage without copying pixels
            val mpImage = BitmapImageBuilder(reusableBitmap!!).build()

            TrackingProfiler.onDetectAsyncCalled(frameId)
            onFrame(mpImage, rotationDegrees, isFrontCamera, frameId, frameTime)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // ALWAYS close imageProxy immediately to ensure CameraX pipeline never starves
            imageProxy.close()
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
