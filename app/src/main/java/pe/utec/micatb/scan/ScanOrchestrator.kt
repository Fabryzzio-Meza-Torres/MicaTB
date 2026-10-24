package pe.utec.micatb.scan

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pe.utec.micatb.data.FieldOutcome
import pe.utec.micatb.data.FieldPos
import pe.utec.micatb.data.InferenceInput
import pe.utec.micatb.data.PhotoInput
import pe.utec.micatb.data.PhotoSource
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.data.db.SessionStatus
import pe.utec.micatb.quality.QualityReport

/** Foto capturada, todavia sin analizar. */
class Frame(val source: PhotoSource, val width: Int, val height: Int) {
    /** Espacio para que el analizador conserve la imagen decodificada entre la calidad y la inferencia. */
    var cache: Any? = null
}

interface FrameSource {
    suspend fun capture(fieldIdx: Int, attempt: Int): Frame
}

interface FieldAnalyzer {
    suspend fun quality(frame: Frame): QualityReport

    suspend fun infer(frame: Frame): InferenceInput

    /** Libera lo que se guardo en [Frame.cache]: una foto de 12 MP ocupa decenas de MB. */
    fun release(frame: Frame) {}
}

/** Lo que el orquestador necesita guardar; SessionRepository lo cumple a traves de un adaptador. */
interface ScanStore {
    suspend fun setSessionStatus(status: SessionStatus)

    suspend fun record(idx: Int, outcome: FieldOutcome)
}

enum class RetryPolicy { PAUSE, SKIP }

data class ScanConfig(val settleMs: Long, val maxRetries: Int, val onRetriesExhausted: RetryPolicy)

enum class ScanPhase {
    IDLE, PREPARING, MOVING, SETTLING, QUALITY_CHECK, CAPTURING, INFERRING, STORING, RETRYING,
    PAUSED, ERROR, CANCELLING, CANCELLED, COMPLETED, FAILED;

    val terminal: Boolean get() = this == CANCELLED || this == COMPLETED || this == FAILED
}

data class ScanState(
    val phase: ScanPhase = ScanPhase.IDLE,
    /** Campo en curso (0 a total - 1). */
    val index: Int = 0,
    val total: Int = 0,
    val attempt: Int = 0,
    val validFields: Int = 0,
    val invalidFields: Int = 0,
    val failedFields: Int = 0,
    val message: String? = null,
    /** En pausa por un problema: se puede omitir el campo en vez de reintentarlo. */
    val canSkip: Boolean = false,
)

/**
 * Un solo flujo secuencial por campo (SPEC secciones 3 y 4): mover, esperar a que la imagen se estabilice,
 * capturar, comprobar calidad, inferir y guardar. Pausar termina el paso atomico en curso y no inicia el
 * siguiente movimiento; cancelar envia STOP de inmediato. Un campo con calidad no valida no cuenta como examinado.
 */
class ScanOrchestrator(
    private val scope: CoroutineScope,
    private val motion: MotionController,
    private val frames: FrameSource,
    private val analyzer: FieldAnalyzer,
    private val store: ScanStore,
    private val config: ScanConfig,
) {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var pauseRequested = false
    private var gate: CompletableDeferred<Gate>? = null
    private val stopSent = AtomicBoolean(false)

    private enum class Gate { RESUME, SKIP }

    /** Inicia (o reanuda desde [startIndex]) el barrido de [positions]. */
    fun start(positions: List<FieldPos>, startIndex: Int = 0): Job {
        check(job?.isActive != true) { "ya hay un barrido en curso" }
        pauseRequested = false
        stopSent.set(false)
        _state.value = ScanState(ScanPhase.PREPARING, index = startIndex, total = positions.size)
        return scope.launch { run(positions, startIndex) }.also { job = it }
    }

    /** Pide pausa: surte efecto al terminar el campo en curso, antes del siguiente movimiento. */
    fun pause() {
        if (!_state.value.phase.terminal && _state.value.phase != ScanPhase.PAUSED) pauseRequested = true
    }

    fun resume() { gate?.complete(Gate.RESUME) }

    /** Omite el campo que causo la pausa (solo si [ScanState.canSkip]). */
    fun skip() { if (_state.value.canSkip) gate?.complete(Gate.SKIP) }

    /** Detiene ya: STOP al controlador de inmediato y cancelacion del flujo. */
    fun cancel() {
        val p = _state.value.phase
        if (p.terminal || p == ScanPhase.IDLE) return
        _state.update { it.copy(phase = ScanPhase.CANCELLING, message = "Cancelando") }
        sendStop()
        job?.cancel()
    }

    suspend fun join() { job?.join() }

    private fun sendStop() {
        if (stopSent.compareAndSet(false, true)) scope.launch(start = CoroutineStart.UNDISPATCHED) { motion.stop() }
    }

    private suspend fun run(positions: List<FieldPos>, startIndex: Int) {
        try {
            store.setSessionStatus(SessionStatus.RUNNING)
            try {
                motion.connect()
                motion.home()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fatal(startIndex, "Preparacion del movimiento: ${e.message ?: "error"}")
                return
            }
            for (i in startIndex until positions.size) {
                if (pauseRequested) {
                    pauseRequested = false
                    awaitGate(i, "En pausa", canSkip = false)
                }
                if (!processField(positions[i])) return
            }
            set(ScanPhase.COMPLETED, positions.size - 1)
            store.setSessionStatus(SessionStatus.COMPLETED)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                sendStop()
                store.setSessionStatus(SessionStatus.CANCELLED)
                _state.update { it.copy(phase = ScanPhase.CANCELLED, message = "Cancelado", canSkip = false) }
            }
            throw e
        }
    }

    /** Devuelve false si el barrido termino por un error fatal. */
    private suspend fun processField(pos: FieldPos): Boolean {
        while (true) {
            set(ScanPhase.MOVING, pos.idx)
            when (val m = motion.moveTo(pos.x, pos.y)) {
                is MoveResult.Done -> break
                is MoveResult.Failed -> {
                    if (m.fatal) return fatal(pos.idx, "Movimiento: ${m.reason}")
                    if (awaitGate(pos.idx, "Movimiento: ${m.reason}", canSkip = true) == Gate.SKIP) {
                        store.record(pos.idx, FieldOutcome(FieldStatus.FAILED_MOTION, 0, null))
                        _state.update { it.copy(failedFields = it.failedFields + 1) }
                        return true
                    }
                }
            }
        }

        var attempt = 1
        var lastFrame: Frame? = null
        var lastQuality: QualityReport? = null
        while (true) {
            set(ScanPhase.SETTLING, pos.idx, attempt)
            delay(config.settleMs)
            set(ScanPhase.CAPTURING, pos.idx, attempt)
            val frame = try {
                frames.capture(pos.idx, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (awaitGate(pos.idx, "Captura: ${e.message ?: "error"}", canSkip = true) == Gate.SKIP) {
                    store.record(pos.idx, FieldOutcome(FieldStatus.FAILED_OTHER, attempt, null))
                    _state.update { it.copy(failedFields = it.failedFields + 1) }
                    return true
                }
                continue
            }

            set(ScanPhase.QUALITY_CHECK, pos.idx, attempt)
            val q = analyzer.quality(frame)
            if (q.valid) {
                set(ScanPhase.INFERRING, pos.idx, attempt)
                val inference = analyzer.infer(frame)
                set(ScanPhase.STORING, pos.idx, attempt)
                store.record(pos.idx, FieldOutcome(FieldStatus.VALID, attempt, qualityJson(q), PhotoInput(frame.source, frame.width, frame.height, attempt), inference))
                analyzer.release(frame)
                _state.update { it.copy(validFields = it.validFields + 1) }
                return true
            }
            lastFrame?.let { if (it !== frame) analyzer.release(it) }
            lastFrame = frame
            lastQuality = q

            if (attempt <= config.maxRetries) {
                set(ScanPhase.RETRYING, pos.idx, attempt, "Calidad insuficiente, reintentando")
                attempt++
                continue
            }
            if (config.onRetriesExhausted == RetryPolicy.PAUSE &&
                awaitGate(pos.idx, "Calidad insuficiente tras $attempt intentos", canSkip = true) == Gate.RESUME
            ) {
                attempt = 1
                continue
            }
            set(ScanPhase.STORING, pos.idx, attempt)
            store.record(pos.idx, FieldOutcome(FieldStatus.INVALID_QUALITY, attempt, qualityJson(lastQuality), PhotoInput(lastFrame.source, lastFrame.width, lastFrame.height, attempt), null))
            analyzer.release(lastFrame)
            _state.update { it.copy(invalidFields = it.invalidFields + 1) }
            return true
        }
    }

    private suspend fun fatal(idx: Int, reason: String): Boolean {
        set(ScanPhase.ERROR, idx, message = reason)
        sendStop()
        store.setSessionStatus(SessionStatus.FAILED)
        _state.update { it.copy(phase = ScanPhase.FAILED, message = reason, canSkip = false) }
        return false
    }

    /** Pausa y espera a la persona: continuar o, si [canSkip], omitir el campo. */
    private suspend fun awaitGate(idx: Int, message: String, canSkip: Boolean): Gate {
        val g = CompletableDeferred<Gate>()
        gate = g
        store.setSessionStatus(SessionStatus.PAUSED)
        _state.update { it.copy(phase = ScanPhase.PAUSED, index = idx, message = message, canSkip = canSkip) }
        try {
            return g.await()
        } finally {
            gate = null
            withContext(NonCancellable) { store.setSessionStatus(SessionStatus.RUNNING) }
            _state.update { it.copy(canSkip = false, message = null) }
        }
    }

    private fun set(phase: ScanPhase, idx: Int = _state.value.index, attempt: Int = 0, message: String? = null) =
        _state.update { it.copy(phase = phase, index = idx, attempt = attempt, message = message, canSkip = false) }

    private fun qualityJson(q: QualityReport) = JSONObject()
        .put("sharpness", q.sharpness.toDouble()).put("brightness", q.brightness.toDouble())
        .put("clipped_fraction", q.clippedFraction.toDouble()).put("flags", JSONArray(q.flags.map { it.name })).toString()
}
