package pe.utec.micatb.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Camara trasera con CameraX: vista previa, captura en resolucion maxima y bloqueo de exposicion,
 * balance de blancos y enfoque para que no cambien entre campos de una sesion.
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraSource(private val context: Context) {
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null

    private val _locked = MutableStateFlow(false)

    /** true cuando exposicion y balance de blancos estan bloqueados. */
    val locked: StateFlow<Boolean> = _locked

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound

    suspend fun bind(owner: LifecycleOwner, view: PreviewView) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.surfaceProvider = view.surfaceProvider }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(selector)
            .build()
        provider.unbindAll()
        camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        imageCapture = capture
        previewView = view
        _locked.value = false
        _bound.value = true
    }

    fun unbind() {
        _bound.value = false
        _locked.value = false
        camera = null
        imageCapture = null
        previewView = null
    }

    /** Toca para enfocar y medir en ese punto; el enfoque se mantiene hasta [unlock] (sin cancelacion automatica). */
    fun focusAt(x: Float, y: Float) {
        val view = previewView ?: return
        val cam = camera ?: return
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
            .disableAutoCancel()
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /** Bloquea exposicion y balance de blancos en su valor actual. El enfoque queda donde lo dejo [focusAt]. */
    fun lock() = setLocks(true)

    fun unlock() {
        setLocks(false)
        camera?.cameraControl?.cancelFocusAndMetering()
    }

    private fun setLocks(on: Boolean) {
        val cam = camera ?: return
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, on)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, on)
            .build()
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(options)
        _locked.value = on
    }

    /** Captura un JPEG en [file] a la resolucion maxima disponible, con la rotacion en EXIF. */
    suspend fun captureStill(file: File) {
        val capture = checkNotNull(imageCapture) { "la camara no esta lista" }
        suspendCancellableCoroutine { cont ->
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(Unit)
                    override fun onError(e: ImageCaptureException) = cont.resumeWithException(e)
                },
            )
        }
    }

    companion object {
        /** Decodifica un JPEG aplicando la rotacion EXIF, para que la imagen quede derecha. */
        fun decodeUpright(file: File): Bitmap = decodeUpright(file.readBytes())

        fun decodeUpright(bytes: ByteArray): Bitmap {
            val raw = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "no se pudo decodificar la imagen" }
            val orientation = ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return raw
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
            if (rotated !== raw) raw.recycle()
            return rotated
        }
    }
}
