package pe.utec.micatb.scan.ble

import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pe.utec.micatb.scan.MoveResult
import pe.utec.micatb.scan.MotionController

/** Eventos del controlador (caracteristica EVT), segun docs/SPEC.md seccion 5. */
sealed interface BleEvent {
    data class HelloOk(val version: Int, val maxX: Int, val maxY: Int, val state: String) : BleEvent
    data class Ack(val id: Int) : BleEvent
    data class Done(val id: Int, val x: Int, val y: Int) : BleEvent
    data class Err(val id: Int, val code: String) : BleEvent

    /** STATE estado, y con STATUS tambien la posicion confirmada. */
    data class State(val name: String, val x: Int?, val y: Int?) : BleEvent
    data object Pong : BleEvent
    data object Disconnected : BleEvent
}

/** Tramas ASCII terminadas en salto de linea, de 64 bytes como maximo: "id VERBO argumentos". */
object BleFrames {
    const val MAX_BYTES = 64

    fun command(id: Int, verb: String, vararg args: Int): String {
        val line = (listOf(id.toString(), verb) + args.map { it.toString() }).joinToString(" ") + "\n"
        require(line.toByteArray().size <= MAX_BYTES) { "trama de ${line.length} bytes" }
        return line
    }

    fun parse(raw: String): BleEvent? {
        val p = raw.trim().split(' ').filter { it.isNotEmpty() }
        return runCatching {
            when (p.firstOrNull()) {
                "HELLO_OK" -> BleEvent.HelloOk(p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4])
                "ACK" -> BleEvent.Ack(p[1].toInt())
                "DONE" -> BleEvent.Done(p[1].toInt(), p[2].toInt(), p[3].toInt())
                "ERR" -> BleEvent.Err(p[1].toInt(), p[2])
                "STATE" -> BleEvent.State(p[1], p.getOrNull(2)?.toInt(), p.getOrNull(3)?.toInt())
                "PONG" -> BleEvent.Pong
                else -> null
            }
        }.getOrNull()
    }
}

/** Canal con el controlador: CMD de escritura y EVT de notificacion. La implementacion GATT real va aparte. */
interface BleTransport {
    /** Lineas recibidas por EVT, sin el salto de linea. */
    val lines: SharedFlow<String>
    val connected: StateFlow<Boolean>

    suspend fun connect()

    /** Escribe una trama por CMD. Lanza si no hay conexion. */
    suspend fun send(line: String)

    fun disconnect()
}

data class BleConfig(
    val ackTimeoutMs: Long = 500,
    val ackRetries: Int = 3,
    /** DONE se espera: tiempo estimado por distancia y velocidad, por 2, mas este margen. */
    val doneMarginMs: Long = 2_000,
    val speedStepsPerSecond: Int = 2_000,
    val pingIntervalMs: Long = 1_000,
    val stopWaitMs: Long = 500,
)

/**
 * MotionController sobre el protocolo del SPEC seccion 5. Solo da un movimiento por terminado con DONE;
 * reintenta la escritura con el mismo id (MOVE_ABS es idempotente) y, ante timeout, error o perdida de
 * conexion, detiene y pide intervencion en vez de reintentar a ciegas. Tras reconectar compara la posicion
 * con el ultimo DONE y, si no coincide, exige HOME antes de seguir.
 *
 * [scope] aloja el lector de eventos y la vigilancia por PING; cancelarlo (o [release]) los detiene.
 */
class BleMotionController(
    private val transport: BleTransport,
    private val scope: CoroutineScope,
    private val config: BleConfig = BleConfig(),
) : MotionController {
    private class Stamped(val seq: Long, val e: BleEvent)

    private val events = MutableSharedFlow<Stamped>(replay = 32, extraBufferCapacity = 64)
    private var seq = 0L
    private var nextId = 0
    private var hello: BleEvent.HelloOk? = null
    private var lastConfirmed: Pair<Int, Int>? = null
    private var homeNext = false
    private var pinger: Job? = null
    private var readers: List<Job> = emptyList()

    var pingCount = 0
        private set

    private fun newId(): Int { nextId = (nextId + 1) and 0xFFFF; return nextId }

    override suspend fun connect() {
        if (readers.isEmpty()) {
            readers = listOf(
                scope.launch { transport.lines.collect { l -> BleFrames.parse(l)?.let { emit(it) } } },
                scope.launch { transport.connected.collect { if (!it) emit(BleEvent.Disconnected) } },
            )
        }
        if (!transport.connected.value) transport.connect()
        val id = newId()
        val reply = request(BleFrames.command(id, "HELLO"), config.ackTimeoutMs * 4) { it is BleEvent.HelloOk }
        hello = reply as? BleEvent.HelloOk ?: error("el controlador no respondio a HELLO")
        startWatchdog()
    }

    /** Lleva el equipo al origen. Es seguro: los limites y finales de carrera los protege el firmware. */
    override suspend fun home() {
        val id = newId()
        val r = confirmed(id, BleFrames.command(id, "HOME"), timeoutMs = 30_000)
        if (r is MoveResult.Done) { lastConfirmed = r.x to r.y; homeNext = false } else error((r as MoveResult.Failed).reason)
    }

    override suspend fun moveTo(x: Int, y: Int): MoveResult {
        val h = hello ?: return MoveResult.Failed("sin conexion con el controlador")
        if (!transport.connected.value) { homeNext = true; return MoveResult.Failed("conexion perdida: reconecta y reintenta") }
        if (x !in 0..h.maxX || y !in 0..h.maxY) return MoveResult.Failed("fuera de limites ($x, $y)")
        if (homeNext) {
            val s = statusPosition()
            if (s != null && s == lastConfirmed) homeNext = false
            else return try { home(); moveTo(x, y) } catch (e: IllegalStateException) { MoveResult.Failed("HOME fallo: ${e.message}") }
        }
        val from = lastConfirmed ?: (0 to 0)
        val estimateMs = (abs(x - from.first) + abs(y - from.second)) * 1000L / config.speedStepsPerSecond
        val id = newId()
        val r = confirmed(id, BleFrames.command(id, "MOVE_ABS", x, y), timeoutMs = estimateMs * 2 + config.doneMarginMs)
        if (r is MoveResult.Done) lastConfirmed = r.x to r.y
        return r
    }

    /** STOP con prioridad: se escribe ya, sin esperar a nada, y el movimiento en curso termina como detenido. */
    override suspend fun stop() {
        val id = newId()
        val since = seq
        runCatching { transport.send(BleFrames.command(id, "STOP")) }
        withTimeoutOrNull(config.stopWaitMs) { events.first { it.seq > since && it.e is BleEvent.State && (it.e as BleEvent.State).name == "STOPPED" } }
    }

    fun release() { pinger?.cancel(); readers.forEach { it.cancel() }; readers = emptyList() }

    // ------------------------------------------------------------------ interno

    private suspend fun emit(e: BleEvent) { events.emit(Stamped(++seq, e)) }

    /** Escribe [line] y espera ACK o DONE de [id], reintentando con el mismo id; despues espera el DONE. */
    private suspend fun confirmed(id: Int, line: String, timeoutMs: Long): MoveResult {
        val since = seq
        var first: BleEvent? = null
        for (attempt in 0..config.ackRetries) {
            first = request(line, config.ackTimeoutMs, since) { relevant(it, id) }
            if (first != null) break
        }
        if (first == null) { stop(); homeNext = true; return MoveResult.Failed("el controlador no responde") }
        val end = if (first is BleEvent.Ack) withTimeoutOrNull(timeoutMs) { events.first { matches(it, since) { e -> relevant(e, id) && e !is BleEvent.Ack } }.e } else first
        return when (end) {
            is BleEvent.Done -> MoveResult.Done(end.x, end.y)
            is BleEvent.Err -> errorResult(end.code)
            is BleEvent.State -> if (end.name == "FAULT") MoveResult.Failed("falla del controlador", fatal = true) else { homeNext = true; MoveResult.Failed("detenido") }
            BleEvent.Disconnected -> { homeNext = true; MoveResult.Failed("conexion perdida: reconecta y reintenta") }
            null -> { stop(); homeNext = true; MoveResult.Failed("tiempo agotado esperando el fin del movimiento") }
            else -> MoveResult.Failed("respuesta inesperada")
        }
    }

    private fun errorResult(code: String): MoveResult.Failed = when (code) {
        "FAULT" -> MoveResult.Failed("falla del controlador", fatal = true)
        "UNHOMED" -> { homeNext = true; MoveResult.Failed("sin referencia de origen: hace falta HOME") }
        "LIMIT" -> MoveResult.Failed("limite fisico")
        "BUSY" -> MoveResult.Failed("controlador ocupado")
        else -> MoveResult.Failed("error $code")
    }

    /** Eventos que resuelven una orden con este id: su ACK, DONE o ERR, un STOPPED o FAULT, o perder la conexion. */
    private fun relevant(e: BleEvent, id: Int) = when (e) {
        is BleEvent.Ack -> e.id == id
        is BleEvent.Done -> e.id == id
        is BleEvent.Err -> e.id == id
        is BleEvent.State -> (e.name == "STOPPED" || e.name == "FAULT") && e.x == null
        BleEvent.Disconnected -> true
        else -> false
    }

    /**
     * Los eventos con id propio pueden venir del historial (el DONE puede llegar antes de que se empiece a esperar);
     * STOPPED, FAULT y las desconexiones solo cuentan si ocurrieron despues de [since], no los de ordenes anteriores.
     */
    private fun matches(s: Stamped, since: Long, match: (BleEvent) -> Boolean): Boolean {
        if (!match(s.e)) return false
        val own = s.e is BleEvent.Ack || s.e is BleEvent.Done || s.e is BleEvent.Err || s.e is BleEvent.HelloOk
        return own || s.seq > since
    }

    /** Escribe una trama y espera la primera respuesta que cumpla [match]; null si vence [timeoutMs]. */
    private suspend fun request(line: String, timeoutMs: Long, since: Long = seq, match: (BleEvent) -> Boolean): BleEvent? =
        coroutineScope {
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(timeoutMs) { events.first { matches(it, since, match) }.e }
            }
            runCatching { transport.send(line) }.onFailure { emit(BleEvent.Disconnected) }
            waiter.await()
        }
    private suspend fun statusPosition(): Pair<Int, Int>? {
        val id = newId()
        val r = request(BleFrames.command(id, "STATUS"), config.ackTimeoutMs * 2) { it is BleEvent.State && it.x != null }
        return (r as? BleEvent.State)?.let { it.x!! to it.y!! }
    }

    private fun startWatchdog() {
        pinger?.cancel()
        pinger = scope.launch {
            while (isActive) {
                delay(config.pingIntervalMs)
                if (transport.connected.value) {
                    pingCount++
                    runCatching { transport.send(BleFrames.command(newId(), "PING")) }
                }
            }
        }
    }
}
