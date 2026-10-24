package pe.utec.micatb.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import pe.utec.micatb.AppContainer
import pe.utec.micatb.camera.CameraSource
import pe.utec.micatb.data.FieldPos
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.data.db.SessionStatus
import pe.utec.micatb.scan.AppFieldAnalyzer
import pe.utec.micatb.scan.CameraFrameSource
import pe.utec.micatb.scan.FrameSource
import pe.utec.micatb.scan.ManualMotionController
import pe.utec.micatb.scan.MotionController
import pe.utec.micatb.scan.RepositoryScanStore
import pe.utec.micatb.scan.RetryPolicy
import pe.utec.micatb.scan.SampleFrameSource
import pe.utec.micatb.scan.ScanConfig
import pe.utec.micatb.scan.ScanOrchestrator
import pe.utec.micatb.scan.ScanState
import pe.utec.micatb.scan.SimulatedMotionController
import pe.utec.micatb.scan.generateSnakePath

enum class MotionMode { MANUAL, SIMULATED }

enum class ImageSource { CAMERA, SAMPLES }

data class SetupState(
    val label: String = "",
    val fields: Int = 10,
    val motion: MotionMode = MotionMode.MANUAL,
    val source: ImageSource = ImageSource.CAMERA,
)

data class UnfinishedSession(val id: String, val label: String?, val createdAt: Long, val done: Int, val total: Int)

/** Resultado de un campo ya procesado, para la lista de progreso. */
data class FieldRow(val idx: Int, val status: FieldStatus, val countStrict: Int?)

data class RunUi(
    val sessionId: String,
    val setup: SetupState,
    val scan: ScanState,
    val manualPrompt: ManualMotionController.Prompt?,
    val rows: List<FieldRow>,
)

class SessionViewModel(private val c: AppContainer) : ViewModel() {
    val camera = CameraSource(c.context)

    private val _setup = MutableStateFlow(SetupState(fields = c.appConfig.session.defaultFields))
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _unfinished = MutableStateFlow<List<UnfinishedSession>>(emptyList())
    val unfinished: StateFlow<List<UnfinishedSession>> = _unfinished.asStateFlow()

    private val _run = MutableStateFlow<RunUi?>(null)
    val run: StateFlow<RunUi?> = _run.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var orchestrator: ScanOrchestrator? = null
    private var manual: ManualMotionController? = null

    init { refreshUnfinished() }

    fun setLabel(v: String) = _setup.update { it.copy(label = v.take(60)) }

    fun setFields(n: Int) = _setup.update { it.copy(fields = n.coerceIn(1, 200)) }

    fun setMotion(m: MotionMode) = _setup.update { it.copy(motion = m) }

    fun setSource(s: ImageSource) = _setup.update { it.copy(source = s) }

    fun dismissError() { _error.value = null }

    fun refreshUnfinished() {
        viewModelScope.launch {
            val activeId = _run.value?.sessionId
            _unfinished.value = c.sessions.unfinished().filter { it.id != activeId }.map {
                val fields = c.sessions.detail(it.id)?.fields.orEmpty()
                UnfinishedSession(it.id, it.sampleLabel, it.createdAt, fields.count { f -> f.field.status != FieldStatus.PENDING.name }, fields.size)
            }
        }
    }

    fun start() {
        val s = _setup.value
        viewModelScope.launch {
            try {
                val cols = 5
                val rows = ceil(s.fields / cols.toDouble()).toInt()
                val positions = generateSnakePath(cols, rows, 1, 1).take(s.fields)
                val config = JSONObject()
                    .put("fields", s.fields).put("motion", s.motion.name).put("source", s.source.name).put("cols", cols)
                    .put("settle_ms", c.appConfig.session.settleMs).put("max_retries", c.appConfig.session.maxRetries)
                    .put("on_retries_exhausted", c.appConfig.session.onRetriesExhausted)
                    .put("strict_conf", c.modelConfig.strict.conf.toDouble()).put("broad_conf", c.modelConfig.broad.conf.toDouble())
                    .put("variant", c.variantName).put("sharpness_min", c.settings.effectiveQuality(c.appConfig.quality).sharpnessMin.toDouble())
                val id = c.sessions.createSession(s.label, c.activeModelId, c.activeSha256, config.toString())
                c.sessions.prepareFields(id, positions)
                launchScan(id, s, positions, startIndex = 0)
            } catch (e: Exception) {
                _error.value = "No se pudo iniciar la sesión: ${e.message ?: "error"}"
            }
        }
    }

    /** Reanuda una sesion interrumpida desde el primer campo sin resultado final (MT-14). */
    fun resumeSession(id: String) {
        viewModelScope.launch {
            try {
                val session = checkNotNull(c.sessions.session(id)) { "la sesión ya no existe" }
                val cfg = JSONObject(session.configJson)
                val s = SetupState(session.sampleLabel.orEmpty(), cfg.getInt("fields"), MotionMode.valueOf(cfg.getString("motion")), ImageSource.valueOf(cfg.getString("source")))
                launchScan(id, s, c.sessions.positions(id), startIndex = c.sessions.resumeIndex(id))
            } catch (e: Exception) {
                _error.value = "No se pudo reanudar: ${e.message ?: "error"}"
            }
        }
    }

    fun discardSession(id: String) {
        viewModelScope.launch { c.sessions.setStatus(id, SessionStatus.CANCELLED); refreshUnfinished() }
    }

    private fun launchScan(id: String, s: SetupState, positions: List<FieldPos>, startIndex: Int) {
        val manualController = if (s.motion == MotionMode.MANUAL) ManualMotionController() else null
        val motion: MotionController = manualController ?: SimulatedMotionController(moveDelayMs = 500)
        val frames: FrameSource = when (s.source) {
            ImageSource.CAMERA -> CameraFrameSource(camera, c.storage.dir("tmp"))
            ImageSource.SAMPLES -> SampleFrameSource(c.context)
        }
        val settle = if (s.source == ImageSource.CAMERA) c.appConfig.session.settleMs else 150L
        val policy = if (c.appConfig.session.onRetriesExhausted == "skip") RetryPolicy.SKIP else RetryPolicy.PAUSE
        if (s.source == ImageSource.CAMERA) camera.lock() // exposicion y balance de blancos fijos para toda la sesion

        val orch = ScanOrchestrator(
            viewModelScope, motion, frames, AppFieldAnalyzer(c), RepositoryScanStore(c.sessions, id),
            ScanConfig(settle, c.appConfig.session.maxRetries, policy),
        )
        orchestrator = orch
        manual = manualController
        _run.value = RunUi(id, s, orch.state.value, null, emptyList())

        viewModelScope.launch {
            val prompt = manualController?.prompt ?: flowOf(null)
            combine(orch.state, prompt) { st, p -> st to p }.collect { (st, p) ->
                _run.update { it?.copy(scan = st, manualPrompt = p) }
            }
        }
        viewModelScope.launch {
            orch.state.map { Triple(it.validFields, it.invalidFields, it.failedFields) }.distinctUntilChanged().collect { refreshRows(id) }
        }
        viewModelScope.launch {
            orch.state.map { it.phase.terminal }.distinctUntilChanged().collect { if (it) { refreshRows(id); if (s.source == ImageSource.CAMERA) camera.unlock() } }
        }
        orch.start(positions, startIndex)
    }

    private suspend fun refreshRows(id: String) {
        val d = c.sessions.detail(id) ?: return
        val rows = d.fields.filter { it.field.status != FieldStatus.PENDING.name }.map { FieldRow(it.field.idx, FieldStatus.valueOf(it.field.status), it.inference?.countStrict) }
        _run.update { if (it?.sessionId == id) it.copy(rows = rows) else it }
    }

    fun pause() { orchestrator?.pause() }

    fun resume() { orchestrator?.resume() }

    fun skip() { orchestrator?.skip() }

    fun cancel() { orchestrator?.cancel() }

    fun confirmMove() { manual?.confirm() }

    /** Cierra el resumen de una sesion terminada y vuelve a la configuracion. */
    fun close() {
        orchestrator = null; manual = null; _run.value = null
        refreshUnfinished()
    }

    override fun onCleared() = camera.unbind()
}
