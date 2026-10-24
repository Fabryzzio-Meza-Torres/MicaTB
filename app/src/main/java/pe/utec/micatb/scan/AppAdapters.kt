package pe.utec.micatb.scan

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.utec.micatb.AppContainer
import pe.utec.micatb.camera.CameraSource
import pe.utec.micatb.data.DetectionsJson
import pe.utec.micatb.data.FieldOutcome
import pe.utec.micatb.data.InferenceInput
import pe.utec.micatb.data.PhotoSource
import pe.utec.micatb.data.SessionRepository
import pe.utec.micatb.data.db.SessionStatus
import pe.utec.micatb.inference.BitmapRows
import pe.utec.micatb.inference.countFor
import pe.utec.micatb.quality.QualityAnalyzer
import pe.utec.micatb.quality.QualityReport

/** Fotos de la camara real, en resolucion maxima y ya con la rotacion EXIF aplicada al decodificar. */
class CameraFrameSource(private val camera: CameraSource, private val tmpDir: File) : FrameSource {
    override suspend fun capture(fieldIdx: Int, attempt: Int): Frame {
        val file = File(tmpDir, "f$fieldIdx-$attempt.jpg")
        camera.captureStill(file)
        val bmp = withContext(Dispatchers.Default) { CameraSource.decodeUpright(file) }
        return Frame(PhotoSource.OnDisk(file), bmp.width, bmp.height).also { it.cache = bmp }
    }
}

/** Plan B de la demo: recorre las muestras del conjunto publico como si fueran los campos de la lamina. */
class SampleFrameSource(private val context: Context) : FrameSource {
    private val names = context.assets.list("samples").orEmpty().sorted()

    override suspend fun capture(fieldIdx: Int, attempt: Int): Frame = withContext(Dispatchers.IO) {
        val bytes = context.assets.open("samples/${names[fieldIdx % names.size]}").use { it.readBytes() }
        val bmp = CameraSource.decodeUpright(bytes)
        Frame(PhotoSource.Bytes(bytes), bmp.width, bmp.height).also { it.cache = bmp }
    }
}

class AppFieldAnalyzer(private val c: AppContainer) : FieldAnalyzer {
    private fun bitmap(frame: Frame): Bitmap = (frame.cache as? Bitmap)?.takeIf { !it.isRecycled } ?: when (val s = frame.source) {
        is PhotoSource.OnDisk -> CameraSource.decodeUpright(s.file)
        is PhotoSource.Bytes -> CameraSource.decodeUpright(s.bytes)
    }.also { frame.cache = it }

    override suspend fun quality(frame: Frame): QualityReport = withContext(Dispatchers.Default) {
        QualityAnalyzer.analyze(BitmapRows(bitmap(frame)), c.settings.effectiveQuality(c.appConfig.quality))
    }

    override suspend fun infer(frame: Frame): InferenceInput = withContext(Dispatchers.Default) {
        val cfg = c.modelConfig
        val raw = c.detector().detect(bitmap(frame))
        InferenceInput(
            modelId = c.activeModelId, baseConf = cfg.post.baseConf, conf = cfg.strict.conf, nmsIou = cfg.post.nmsIou,
            countStrict = raw.detections.countFor(cfg.strict, cfg.classes), countBroad = raw.detections.countFor(cfg.broad, cfg.classes),
            detectionsJson = DetectionsJson.encode(raw.detections),
            tPreMs = raw.timings.preMs, tInferMs = raw.timings.inferMs, tPostMs = raw.timings.postMs,
        )
    }

    override fun release(frame: Frame) {
        (frame.cache as? Bitmap)?.recycle()
        frame.cache = null
    }
}

/** Puente entre el orquestador y SessionRepository; borra el temporal de la camara una vez guardado. */
class RepositoryScanStore(private val repo: SessionRepository, private val sessionId: String) : ScanStore {
    override suspend fun setSessionStatus(status: SessionStatus) = repo.setStatus(sessionId, status)

    override suspend fun record(idx: Int, outcome: FieldOutcome) {
        repo.record(sessionId, idx, outcome)
        (outcome.photo?.source as? PhotoSource.OnDisk)?.file?.delete()
    }
}
