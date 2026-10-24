package pe.utec.micatb.ui.capture

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.utec.micatb.AppContainer
import pe.utec.micatb.FieldAnalysis
import pe.utec.micatb.camera.CameraSource
import pe.utec.micatb.data.CountMode
import pe.utec.micatb.data.ResultJson
import pe.utec.micatb.domain.CountingRule

enum class Phase { CAMERA, WORKING, RESULT }

data class CaptureUiState(
    val phase: Phase = Phase.CAMERA,
    val mode: CountMode = CountMode.STRICT,
    /** Umbral de la regla activa. El deslizador lo mueve sin reejecutar el modelo. */
    val conf: Float = 0.45f,
    val bitmap: Bitmap? = null,
    val analysis: FieldAnalysis? = null,
    val sourceLabel: String = "",
    val savedAs: String? = null,
    val error: String? = null,
)

private sealed interface Original {
    class OnDisk(val file: File) : Original
    class Bytes(val bytes: ByteArray) : Original
}

class CaptureViewModel(private val c: AppContainer) : ViewModel() {
    val camera = CameraSource(c.context)

    private val _state = MutableStateFlow(
        CaptureUiState(mode = c.settings.countMode, conf = rule(c.settings.countMode).conf),
    )
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var original: Original? = null

    private fun rule(mode: CountMode): CountingRule = if (mode == CountMode.STRICT) c.modelConfig.strict else c.modelConfig.broad

    fun activeRule(): CountingRule = rule(_state.value.mode)

    val samples: List<String> by lazy { c.context.assets.list("samples")?.sorted().orEmpty() }

    fun setMode(mode: CountMode) {
        c.settings.countMode = mode
        _state.update { it.copy(mode = mode, conf = rule(mode).conf, savedAs = null) }
    }

    fun setConf(conf: Float) = _state.update { it.copy(conf = conf) }

    fun capture() {
        if (_state.value.phase != Phase.CAMERA) return
        _state.update { it.copy(phase = Phase.WORKING, error = null) }
        viewModelScope.launch {
            try {
                val file = File(c.storage.dir("tmp"), "capture.jpg")
                camera.captureStill(file)
                val (bmp, analysis) = withContext(Dispatchers.Default) { CameraSource.decodeUpright(file).let { it to c.analyze(it) } }
                original = Original.OnDisk(file)
                show(bmp, analysis, "Cámara")
            } catch (e: Exception) {
                fail("No se pudo capturar: ${e.message ?: "error de cámara"}")
            }
        }
    }

    fun loadSample(name: String) = load("Muestra $name") { c.context.assets.open("samples/$name").use { it.readBytes() } }

    fun loadUri(uri: Uri) = load("Galería") { c.context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }

    private fun load(label: String, read: () -> ByteArray) {
        _state.update { it.copy(phase = Phase.WORKING, error = null) }
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { read() }
                val (bmp, analysis) = withContext(Dispatchers.Default) { CameraSource.decodeUpright(bytes).let { it to c.analyze(it) } }
                original = Original.Bytes(bytes)
                show(bmp, analysis, label)
            } catch (e: Exception) {
                fail("No se pudo abrir la imagen: ${e.message ?: "formato no válido"}")
            }
        }
    }

    private fun show(bmp: Bitmap, analysis: FieldAnalysis, label: String) = _state.update {
        it.copy(phase = Phase.RESULT, bitmap = bmp, analysis = analysis, sourceLabel = label, savedAs = null, conf = rule(it.mode).conf)
    }

    private fun fail(message: String) = _state.update { it.copy(phase = Phase.CAMERA, error = message) }

    fun retake() {
        original = null
        _state.update { it.copy(phase = Phase.CAMERA, bitmap = null, analysis = null, savedAs = null, error = null) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Guarda la foto original, sin cajas, y el JSON del resultado (RF18). */
    fun save() {
        val s = _state.value
        val analysis = s.analysis ?: return
        val orig = original ?: return
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
                    val json = ResultJson.build(
                        config = c.modelConfig, variant = c.variantName, sha256 = c.activeSha256,
                        raw = analysis.raw, quality = analysis.quality,
                        strictConf = if (s.mode == CountMode.STRICT) s.conf else c.modelConfig.strict.conf,
                        broadConf = if (s.mode == CountMode.BROAD) s.conf else c.modelConfig.broad.conf,
                        capturedAtMs = now, source = s.sourceLabel,
                    )
                    when (orig) {
                        is Original.OnDisk -> c.storage.commit("adhoc", id, orig.file, json)
                        is Original.Bytes -> c.storage.commit("adhoc", id, orig.bytes, json)
                    }
                    id
                }
                _state.update { it.copy(savedAs = id) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "No se pudo guardar: ${e.message ?: "error de almacenamiento"}") }
            }
        }
    }

    override fun onCleared() = camera.unbind()
}
