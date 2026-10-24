package pe.utec.micatb.scan.ble

import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controlador ESP32 simulado que habla el protocolo de docs/SPEC.md seccion 5, para desarrollar y probar
 * sin hardware. Permite inyectar perdida de ACK, DONE tardio o repetido, errores y desconexion.
 * Un id repetido reenvia sus respuestas sin volver a ejecutar el movimiento (idempotencia).
 */
class SimulatedEsp32(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    val faults: Faults = Faults(),
    val maxX: Int = 20_000,
    val maxY: Int = 20_000,
    private val stepsPerSecond: Int = 5_000,
) : BleTransport {
    class Faults(
        /** Cuantas transmisiones de ACK se pierden (la orden si se ejecuta). */
        var dropAcks: Int = 0,
        var doneDelayMs: Long = 0,
        var duplicateDone: Boolean = false,
        /** Codigo de error con el que responde a MOVE_ABS (LIMIT, BUSY, FAULT...). */
        var failMoveWith: String? = null,
    )

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val lines: SharedFlow<String> = _lines.asSharedFlow()
    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    var state = "BOOT"
        private set

    /** Posicion fisica real, que puede diferir de la ultima confirmada si algo interrumpe un movimiento. */
    var x = 0
        private set
    var y = 0
        private set

    val executedMoves = mutableListOf<Pair<Int, Int>>()
    val received = mutableListOf<String>()
    var homeCount = 0
        private set
    var stopCount = 0
        private set

    private val seen = HashMap<Int, MutableList<String>>()
    private var motion: Job? = null
    private var segment: Segment? = null
    private var lastPing = 0L
    private var watchdog: Job? = null

    private class Segment(val startMs: Long, val durationMs: Long, val fromX: Int, val fromY: Int, val toX: Int, val toY: Int)

    override suspend fun connect() { _connected.value = true }

    override fun disconnect() {
        if (_connected.value) { _connected.value = false; stopMotion(announce = false) }
    }

    override suspend fun send(line: String) {
        if (!_connected.value) throw IOException("sin conexion")
        val text = line.trim()
        received += text
        if (text.substringAfter(' ').substringBefore(' ') == "STOP") { stopMotion(announce = true); return } // prioridad
        scope.launch { handle(text) }
    }

    private suspend fun handle(text: String) {
        val p = text.split(' ').filter { it.isNotEmpty() }
        val id = p.getOrNull(0)?.toIntOrNull() ?: return
        val verb = p.getOrNull(1) ?: return
        if (verb != "PING" && verb != "STATUS" && verb != "HELLO") {
            seen[id]?.let { replies -> replies.forEach { emitText(it) }; return } // id repetido: no se re-ejecuta
        }
        when (verb) {
            "HELLO" -> {
                if (state == "BOOT") state = "UNHOMED"
                lastPing = now(); startWatchdog()
                emitText("HELLO_OK 1 $maxX $maxY $state")
            }
            "PING" -> { lastPing = now(); emitText("PONG") }
            "STATUS" -> emitText("STATE $state $x $y")
            "HOME" -> if (busy()) reply(id, "ERR $id BUSY") else {
                reply(id, "ACK $id"); state = "HOMING"; emitText("STATE HOMING")
                motion = scope.launch {
                    delay(500); x = 0; y = 0; homeCount++; state = "IDLE"; emitText("STATE IDLE"); reply(id, "DONE $id 0 0")
                }
            }
            "MOVE_ABS" -> {
                val tx = p.getOrNull(2)?.toIntOrNull() ?: return
                val ty = p.getOrNull(3)?.toIntOrNull() ?: return
                when {
                    state == "BOOT" || state == "UNHOMED" -> reply(id, "ERR $id UNHOMED")
                    busy() -> reply(id, "ERR $id BUSY")
                    tx !in 0..maxX || ty !in 0..maxY -> reply(id, "ERR $id LIMIT")
                    faults.failMoveWith != null -> reply(id, "ERR $id ${faults.failMoveWith}")
                    else -> {
                        executedMoves += tx to ty
                        reply(id, "ACK $id"); state = "MOVING"; emitText("STATE MOVING")
                        val duration = (abs(tx - x) + abs(ty - y)) * 1000L / stepsPerSecond + faults.doneDelayMs
                        segment = Segment(now(), duration, x, y, tx, ty)
                        motion = scope.launch {
                            delay(duration)
                            x = tx; y = ty; segment = null; state = "IDLE"; emitText("STATE IDLE")
                            reply(id, "DONE $id $tx $ty")
                            if (faults.duplicateDone) emitText("DONE $id $tx $ty")
                        }
                    }
                }
            }
        }
    }

    private fun busy() = state == "MOVING" || state == "HOMING"

    private fun stopMotion(announce: Boolean) {
        stopCount++
        motion?.cancel(); motion = null
        segment?.let { s ->
            val f = if (s.durationMs <= 0) 1.0 else ((now() - s.startMs).toDouble() / s.durationMs).coerceIn(0.0, 1.0)
            x = s.fromX + ((s.toX - s.fromX) * f).toInt(); y = s.fromY + ((s.toY - s.fromY) * f).toInt()
        }
        segment = null
        if (state != "BOOT" && state != "UNHOMED") state = "STOPPED"
        if (announce) scope.launch { emitText("STATE STOPPED") }
    }

    /** Vigilancia: sin PING en 3 s el firmware se detiene por su cuenta. */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(500)
                if (_connected.value && busy() && now() - lastPing > 3_000) { stopMotion(announce = true) }
            }
        }
    }

    private suspend fun reply(id: Int, text: String) { seen.getOrPut(id) { mutableListOf() }.add(text); emitText(text) }

    private suspend fun emitText(text: String) {
        if (text.startsWith("ACK") && faults.dropAcks > 0) { faults.dropAcks--; return }
        if (_connected.value) _lines.emit(text)
    }
}
