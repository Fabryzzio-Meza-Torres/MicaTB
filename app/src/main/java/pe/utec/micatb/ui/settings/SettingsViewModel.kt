package pe.utec.micatb.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.utec.micatb.AppContainer
import pe.utec.micatb.camera.CameraSource
import pe.utec.micatb.inference.Benchmark
import pe.utec.micatb.inference.BenchmarkResult
import pe.utec.micatb.inference.BitmapRows
import pe.utec.micatb.quality.QualityAnalyzer

data class SettingsUi(
    /** "fp16" o "int8". */
    val variant: String,
    val threads: Int,
    val sharpnessMin: Float,
    val calibrated: Boolean,
    val fieldWidthUm: Float?,
)

sealed interface BenchState {
    data object Idle : BenchState
    data class Running(val label: String, val done: Int, val total: Int) : BenchState
    data class Done(val results: List<BenchmarkResult>, val file: String) : BenchState
    data class Failed(val message: String) : BenchState
}

/** Puntajes de nitidez de fotos enfocadas y desenfocadas con el microscopio real, para sugerir el umbral. */
data class CalibrationState(val sharp: List<Float> = emptyList(), val blurred: List<Float> = emptyList()) {
    /** Punto medio entre la peor enfocada y la mejor desenfocada; null si se solapan o faltan datos. */
    val suggestion: Float?
        get() = if (sharp.isNotEmpty() && blurred.isNotEmpty() && sharp.min() > blurred.max()) (sharp.min() + blurred.max()) / 2 else null

    val overlaps: Boolean get() = sharp.isNotEmpty() && blurred.isNotEmpty() && sharp.min() <= blurred.max()
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(read())
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    private val _bench = MutableStateFlow<BenchState>(BenchState.Idle)
    val bench: StateFlow<BenchState> = _bench.asStateFlow()

    private val _calib = MutableStateFlow(CalibrationState())
    val calib: StateFlow<CalibrationState> = _calib.asStateFlow()

    private fun read(): SettingsUi {
        val q = c.settings.effectiveQuality(c.appConfig.quality)
        return SettingsUi(c.variantName, c.settings.threads, q.sharpnessMin, q.calibrated, c.settings.fieldWidthUm)
    }

    fun setVariant(v: String) { c.settings.modelVariant = if (v == "fp16") null else v; _ui.value = read() }

    fun setThreads(n: Int) { c.settings.threads = n; _ui.value = read() }

    fun setFieldWidth(text: String) {
        c.settings.fieldWidthUm = text.replace(',', '.').toFloatOrNull()?.takeIf { it > 0 }
        _ui.value = read()
    }

    fun runBenchmark() {
        if (_bench.value is BenchState.Running) return
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.Default) {
                    val image = c.context.assets.open("samples/901699.jpg").use { CameraSource.decodeUpright(it.readBytes()) }
                    val variants = listOf<String?>(null) + c.modelConfig.variants.keys
                    variants.mapIndexed { i, v ->
                        val name = (v ?: "fp16").uppercase()
                        _bench.value = BenchState.Running("$name, ${c.settings.threads} hilos", 0, Benchmark.RUNS)
                        Benchmark.run(c.context, c.modelConfig, v, c.settings.threads, image) { done ->
                            _bench.value = BenchState.Running("$name, ${c.settings.threads} hilos", done, Benchmark.RUNS)
                        }.also { _ -> if (i == variants.lastIndex) image.recycle() }
                    }
                }
                val now = System.currentTimeMillis()
                val file = withContext(Dispatchers.IO) {
                    val dir = File(c.context.getExternalFilesDir(null) ?: c.context.filesDir, "benchmarks").apply { mkdirs() }
                    File(dir, "bench-$now.json").apply { writeText(Benchmark.toJson(results, "901699.jpg", c.modelConfig, now).toString(1)) }
                }
                _bench.value = BenchState.Done(results, file.absolutePath)
            } catch (e: Exception) {
                _bench.value = BenchState.Failed(e.message ?: "falló el benchmark")
            }
        }
    }

    fun addCalibrationPhoto(uri: Uri, sharp: Boolean) {
        viewModelScope.launch {
            try {
                val score = withContext(Dispatchers.Default) {
                    val bmp = CameraSource.decodeUpright(c.context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                    QualityAnalyzer.analyze(BitmapRows(bmp), c.appConfig.quality).sharpness.also { bmp.recycle() }
                }
                _calib.update { if (sharp) it.copy(sharp = it.sharp + score) else it.copy(blurred = it.blurred + score) }
            } catch (e: Exception) {
                _bench.value = BenchState.Failed("No se pudo leer la foto: ${e.message}")
            }
        }
    }

    fun clearCalibration() { _calib.value = CalibrationState() }

    fun applyCalibration() {
        _calib.value.suggestion?.let { c.settings.sharpnessMin = it; _ui.value = read() }
    }

    fun resetSharpness() { c.settings.sharpnessMin = null; _ui.value = read() }
}
