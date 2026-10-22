package ai.signora.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
 * native upright portrait rotation and hardware mirroring, and dedicated single-thread execution.
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
    private var preview: Preview? = null
    private var activePreviewView: PreviewView? = previewView

    // Pre-allocated reusable buffers across frames (zero per-frame heap allocation)
    private var rawBitmap: Bitmap? = null
    private var pingPongIndex = 0
    private val portraitBitmaps = arrayOfNulls<Bitmap>(2)
    private val rotateCanvases = arrayOfNulls<Canvas>(2)
    private var rotateMatrix: Matrix? = null
    private var lastRot = -1
    private var lastW = -1
    private var lastH = -1
    private var lastFront: Boolean? = null

    var isFrontCamera: Boolean = true
        private set

    var isTorchOn: Boolean = false
        private set

    var isPaused: Boolean = false

    var onResolutionChanged: ((Int, Int) -> Unit)? = null
    private var reportedW = 0
    private var reportedH = 0

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
        recycleBitmaps()
        bindCameraUseCases()
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return false
        if (!cam.cameraInfo.hasFlashUnit()) return false
        isTorchOn = !isTorchOn
        cam.cameraControl.enableTorch(isTorchOn)
        return isTorchOn
    }

    fun attachPreviewView(view: PreviewView) {
        this.activePreviewView = view
        val executor = ContextCompat.getMainExecutor(context)
        preview?.setSurfaceProvider(executor, view.surfaceProvider)
    }

    fun focus(x: Float, y: Float) {
        val cam = camera ?: return
        try {
            val view = activePreviewView ?: previewView
            val factory = view.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point).build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Enforce synchronized 4:3 aspect ratio and 640x480 landscape sensor resolution
            // across Preview and ImageAnalysis so the viewfinder FOV matches neural input 1:1.
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                )
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
            val previewBuilder = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
            try {
                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            } catch (_: Throwable) {}

            val view = activePreviewView ?: previewView
            val executor = ContextCompat.getMainExecutor(context)

            val previewInstance = previewBuilder.build().also {
                it.setSurfaceProvider(executor, view.surfaceProvider)
            }
            this.preview = previewInstance

            @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
            val analysisBuilder = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            try {
                Camera2Interop.Extender(analysisBuilder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            } catch (_: Throwable) {}

            val imageAnalysis = analysisBuilder.build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewInstance,
                imageAnalysis
            )
        } catch (exc: Throwable) {
            exc.printStackTrace()
            // Robust fallback: bind with default basic use cases if resolution selector or interop failed
            try {
                val view = activePreviewView ?: previewView
                val executor = ContextCompat.getMainExecutor(context)
                val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val fallbackPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(executor, view.surfaceProvider)
                }
                this.preview = fallbackPreview
                val fallbackAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build().also {
                        it.setAnalyzer(cameraExecutor) { img -> processImageProxy(img) }
                    }
                camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, fallbackPreview, fallbackAnalysis)
            } catch (fallbackExc: Throwable) {
                fallbackExc.printStackTrace()
            }
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
            val rot = imageProxy.imageInfo.rotationDegrees

            // Destination upright dimensions (swapped if 90 or 270)
            val isRotated = rot == 90 || rot == 270
            val targetW = if (isRotated) height else width
            val targetH = if (isRotated) width else height

            if (targetW != reportedW || targetH != reportedH) {
                reportedW = targetW
                reportedH = targetH
                onResolutionChanged?.invoke(targetW, targetH)
            }

            // Pre-allocate raw input buffer
            if (rawBitmap == null || rawBitmap?.width != width || rawBitmap?.height != height) {
                rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }

            // Pre-allocate destination upright portrait buffer and transformation matrix
            val bufIdx = pingPongIndex % 2
            pingPongIndex++

            if (portraitBitmaps[bufIdx] == null || portraitBitmaps[bufIdx]?.width != targetW || portraitBitmaps[bufIdx]?.height != targetH) {
                portraitBitmaps[bufIdx] = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                rotateCanvases[bufIdx] = Canvas(portraitBitmaps[bufIdx]!!)
            }

            if (rotateMatrix == null || lastRot != rot || lastW != width || lastH != height || lastFront != isFrontCamera) {
                val matrix = Matrix()
                matrix.postTranslate(-width / 2f, -height / 2f)
                matrix.postRotate(rot.toFloat())
                if (isFrontCamera) {
                    matrix.postScale(-1f, 1f)
                }
                matrix.postTranslate(targetW / 2f, targetH / 2f)
                rotateMatrix = matrix
                lastRot = rot
                lastW = width
                lastH = height
                lastFront = isFrontCamera
            }

            // Zero-allocation direct native pixel copy
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            rawBitmap!!.copyPixelsFromBuffer(buffer)

            val destBitmap = portraitBitmaps[bufIdx]!!
            val destCanvas = rotateCanvases[bufIdx]!!

            // Sub-millisecond hardware blit into upright mirrored portrait bitmap
            destCanvas.drawBitmap(rawBitmap!!, rotateMatrix!!, null)

            TrackingProfiler.onConversionDone(frameId)

            // Wrap in MPImage without copying pixels
            val mpImage = BitmapImageBuilder(destBitmap).build()

            TrackingProfiler.onDetectAsyncCalled(frameId)
            onFrame(mpImage, 0, isFrontCamera, frameId, frameTime)
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
            recycleBitmaps()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun recycleBitmaps() {
        try {
            rawBitmap?.recycle()
            rawBitmap = null
            portraitBitmaps[0]?.recycle()
            portraitBitmaps[0] = null
            portraitBitmaps[1]?.recycle()
            portraitBitmaps[1] = null
            rotateCanvases[0] = null
            rotateCanvases[1] = null
            rotateMatrix = null
            lastRot = -1
            lastW = -1
            lastH = -1
            lastFront = null
        } catch (_: Throwable) {
        }
    }
}
