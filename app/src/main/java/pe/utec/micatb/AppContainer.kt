package pe.utec.micatb

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import java.io.File
import pe.utec.micatb.data.CaptureStorage
import pe.utec.micatb.data.SessionRepository
import pe.utec.micatb.data.SettingsStore
import pe.utec.micatb.data.db.MicaDatabase
import pe.utec.micatb.domain.AppConfig
import pe.utec.micatb.domain.ModelConfig
import pe.utec.micatb.inference.BitmapRows
import pe.utec.micatb.inference.LiteRtDetector
import pe.utec.micatb.inference.RawDetections
import pe.utec.micatb.quality.QualityAnalyzer
import pe.utec.micatb.quality.QualityReport

/** Analisis de un campo: calidad de imagen y detecciones crudas. */
data class FieldAnalysis(val quality: QualityReport, val raw: RawDetections)

/** Inyeccion manual por constructor (sin Hilt): lo compartido vive aqui. */
class AppContainer(val context: Context) {
    val modelConfig: ModelConfig = context.assets.open("model_config.json").use { ModelConfig.parse(it.readBytes().decodeToString()) }
    val appConfig: AppConfig = context.assets.open("app_config.json").use { AppConfig.parse(it.readBytes().decodeToString()) }
    val settings = SettingsStore(context)
    val storage = CaptureStorage(context)
    val database: MicaDatabase = Room.databaseBuilder(context, MicaDatabase::class.java, "mica.db").build()
    val sessions = SessionRepository(database, File(context.filesDir, "sessions"))

    private var detector: LiteRtDetector? = null
    private var detectorKey: Pair<String?, Int>? = null

    /** Variante activa: null = FP16 de model_config.json. */
    val variantName: String get() = settings.modelVariant ?: "fp16"

    val activeSha256: String
        get() = settings.modelVariant?.let { modelConfig.variants.getValue(it).sha256 } ?: modelConfig.sha256

    @Synchronized
    fun detector(): LiteRtDetector {
        val key = settings.modelVariant to settings.threads
        if (detector == null || detectorKey != key) {
            detector?.close()
            detector = LiteRtDetector(context, modelConfig, key.first, key.second)
            detectorKey = key
        }
        return detector!!
    }

    fun analyze(bitmap: Bitmap): FieldAnalysis {
        val quality = QualityAnalyzer.analyze(BitmapRows(bitmap), settings.effectiveQuality(appConfig.quality))
        return FieldAnalysis(quality, detector().detect(bitmap))
    }
}
