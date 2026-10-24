package pe.utec.micatb.scan

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MoveResult {
    data class Done(val x: Int, val y: Int) : MoveResult

    /** [fatal] = no se puede reintentar: el barrido se detiene. Si no, queda en pausa para intervenir. */
    data class Failed(val reason: String, val fatal: Boolean = false) : MoveResult
}

/**
 * El orquestador no sabe si el movimiento es manual, simulado o por BLE (SPEC seccion 3).
 * moveTo solo termina con confirmacion real de que el movimiento acabo; stop se atiende de inmediato.
 */
interface MotionController {
    suspend fun connect() {}

    suspend fun home() {}

    suspend fun moveTo(x: Int, y: Int): MoveResult

    suspend fun stop()
}

/**
 * Movimiento a mano: la app pide mover la lamina y espera que la persona confirme.
 * La pantalla observa [prompt] y llama a [confirm].
 */
class ManualMotionController : MotionController {
    data class Prompt(val x: Int, val y: Int)

    private val _prompt = MutableStateFlow<Prompt?>(null)
    val prompt: StateFlow<Prompt?> = _prompt.asStateFlow()
    private var pending: CompletableDeferred<MoveResult>? = null

    override suspend fun moveTo(x: Int, y: Int): MoveResult {
        val d = CompletableDeferred<MoveResult>()
        pending = d
        _prompt.value = Prompt(x, y)
        try {
            return d.await()
        } finally {
            _prompt.value = null
            pending = null
        }
    }

    fun confirm() {
        val p = _prompt.value ?: return
        pending?.complete(MoveResult.Done(p.x, p.y))
    }

    override suspend fun stop() {
        pending?.complete(MoveResult.Failed("detenido"))
    }
}

/**
 * Controlador simulado con retardos y fallos configurables, para construir y probar todo sin hardware.
 * [inject] recibe el numero de llamada (desde 1) y devuelve un fallo para esa llamada, o null.
 */
class SimulatedMotionController(
    private val moveDelayMs: Long = 600,
    private val inject: (Int) -> MoveResult.Failed? = { null },
) : MotionController {
    var moveCalls = 0
        private set
    var stopCalls = 0
        private set
    private var current: CompletableDeferred<Unit>? = null

    override suspend fun moveTo(x: Int, y: Int): MoveResult {
        moveCalls++
        val call = moveCalls
        val stopSignal = CompletableDeferred<Unit>().also { current = it }
        delay(moveDelayMs)
        if (stopSignal.isCompleted) return MoveResult.Failed("detenido")
        return inject(call) ?: MoveResult.Done(x, y)
    }

    override suspend fun stop() {
        stopCalls++
        current?.complete(Unit)
    }
}
