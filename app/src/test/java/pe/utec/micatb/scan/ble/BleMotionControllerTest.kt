package pe.utec.micatb.scan.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.utec.micatb.scan.MoveResult

@OptIn(ExperimentalCoroutinesApi::class)
class BleMotionControllerTest {
    private class Rig(t: TestScope, faults: SimulatedEsp32.Faults = SimulatedEsp32.Faults()) {
        val esp = SimulatedEsp32(t.backgroundScope, { t.testScheduler.currentTime }, faults)
        val ctl = BleMotionController(esp, t.backgroundScope)
    }

    private suspend fun Rig.ready() { ctl.connect(); ctl.home() }

    private fun fail(r: MoveResult) = r as MoveResult.Failed

    // --- tramas ---

    @Test fun framesRespectTheSixtyFourByteLimitAndParseEveryEvent() {
        assertTrue(BleFrames.command(65535, "MOVE_ABS", 1_000_000, 1_000_000).toByteArray().size <= 64)
        assertEquals("7 MOVE_ABS 10 20\n", BleFrames.command(7, "MOVE_ABS", 10, 20))
        assertEquals(BleEvent.HelloOk(1, 100, 200, "UNHOMED"), BleFrames.parse("HELLO_OK 1 100 200 UNHOMED"))
        assertEquals(BleEvent.Ack(3), BleFrames.parse("ACK 3"))
        assertEquals(BleEvent.Done(3, 10, 20), BleFrames.parse("DONE 3 10 20"))
        assertEquals(BleEvent.Err(3, "LIMIT"), BleFrames.parse("ERR 3 LIMIT"))
        assertEquals(BleEvent.State("IDLE", 5, 6), BleFrames.parse("STATE IDLE 5 6"))
        assertEquals(BleEvent.State("STOPPED", null, null), BleFrames.parse("STATE STOPPED"))
        assertEquals(BleEvent.Pong, BleFrames.parse("PONG"))
        assertNull(BleFrames.parse("BASURA 1 2"))
    }

    // --- orden y confirmacion ---

    @Test fun moveBeforeHomeIsRejectedByTheFirmwareAndNothingMoves() = runTest {
        val r = Rig(this); r.ctl.connect()
        val res = fail(r.ctl.moveTo(100, 100))
        assertTrue(res.reason, "HOME" in res.reason)
        assertTrue(r.esp.executedMoves.isEmpty())
    }

    @Test fun moveIsOnlyDoneWithTheControllersConfirmation() = runTest {
        val r = Rig(this); r.ready()
        assertEquals(MoveResult.Done(1000, 500), r.ctl.moveTo(1000, 500))
        assertEquals(1000 to 500, r.esp.x to r.esp.y)
        assertEquals(listOf(1000 to 500), r.esp.executedMoves)
    }

    @Test fun appValidatesLimitsBeforeSending() = runTest {
        val r = Rig(this); r.ready()
        val sent = r.esp.received.size
        assertTrue(fail(r.ctl.moveTo(25_000, 0)).reason.contains("limites"))
        assertEquals(sent, r.esp.received.size) // no se envio nada
    }

    // --- perdida y duplicados ---

    @Test fun lostAcksAreRetriedWithTheSameIdAndTheMoveRunsOnce() = runTest {
        val r = Rig(this); r.ready()
        r.esp.faults.dropAcks = 2
        r.esp.faults.doneDelayMs = 3_000 // el movimiento dura mas que las esperas de ACK, asi que hay que reintentar
        assertEquals(MoveResult.Done(2000, 0), r.ctl.moveTo(2000, 0))
        val moves = r.esp.received.filter { it.contains("MOVE_ABS") }
        assertEquals(3, moves.size)
        assertEquals(1, moves.map { it.substringBefore(' ') }.toSet().size) // mismo id
        assertEquals(1, r.esp.executedMoves.size) // se ejecuto una sola vez
    }

    @Test fun lateDoneWithinTheDeadlineStillSucceeds() = runTest {
        val r = Rig(this); r.ready()
        r.esp.faults.doneDelayMs = 1_500
        assertEquals(MoveResult.Done(3000, 0), r.ctl.moveTo(3000, 0))
    }

    @Test fun aRepeatedDoneNeverConfirmsTheNextMove() = runTest {
        val r = Rig(this); r.ready()
        r.esp.faults.duplicateDone = true
        assertEquals(MoveResult.Done(1000, 0), r.ctl.moveTo(1000, 0))
        assertEquals(MoveResult.Done(2000, 0), r.ctl.moveTo(2000, 0))
        assertEquals(listOf(1000 to 0, 2000 to 0), r.esp.executedMoves)
        assertEquals(2000, r.esp.x)
    }

    // --- errores ---

    @Test fun missingDoneStopsTheEquipmentAndAsksForIntervention() = runTest {
        val r = Rig(this); r.ready()
        r.esp.faults.doneDelayMs = 60_000
        val res = fail(r.ctl.moveTo(1000, 0))
        assertTrue(res.reason, "tiempo agotado" in res.reason)
        assertTrue(!res.fatal)
        assertTrue(r.esp.stopCount >= 1)
    }

    @Test fun firmwareFaultIsFatalAndLimitIsNot() = runTest {
        val r = Rig(this); r.ready()
        r.esp.faults.failMoveWith = "FAULT"
        assertTrue(fail(r.ctl.moveTo(10, 10)).fatal)
        r.esp.faults.failMoveWith = "LIMIT"
        val limit = fail(r.ctl.moveTo(10, 10))
        assertTrue(!limit.fatal && limit.reason.contains("limite"))
    }

    // --- STOP, desconexion y vigilancia ---

    @Test fun stopIsSentAtOnceAndEndsTheMoveAsStopped() = runTest {
        val r = Rig(this); r.ready()
        val move = async { r.ctl.moveTo(15_000, 15_000) }
        advanceTimeBy(300)
        r.ctl.stop()
        assertEquals("detenido", fail(move.await()).reason)
        assertEquals("STOPPED", r.esp.state)
        assertTrue(r.esp.x in 1 until 15_000) // quedo a medio camino
    }

    @Test fun lostConnectionStopsAndAfterReconnectingHomeIsRequiredBeforeMoving() = runTest {
        val r = Rig(this); r.ready()
        assertEquals(MoveResult.Done(1000, 0), r.ctl.moveTo(1000, 0))
        val move = async { r.ctl.moveTo(15_000, 0) }
        advanceTimeBy(300)
        r.esp.disconnect()
        assertTrue(fail(move.await()).reason.contains("conexion perdida"))
        assertTrue(r.esp.x != 1000) // la posicion fisica ya no coincide con el ultimo DONE

        val homes = r.esp.homeCount
        r.ctl.connect() // reconecta
        assertEquals(MoveResult.Done(500, 0), r.ctl.moveTo(500, 0))
        assertEquals(homes + 1, r.esp.homeCount) // exigio HOME antes del movimiento
    }

    @Test fun pingsEverySecondAndTheFirmwareStopsOnItsOwnWithoutThem() = runTest {
        val r = Rig(this); r.ready()
        r.esp.faults.doneDelayMs = 20_000 // movimiento largo: el controlador espera su DONE hasta unos 40 s
        val move = async { r.ctl.moveTo(20_000, 20_000) }
        advanceTimeBy(5_200)
        assertTrue("pings: ${r.ctl.pingCount}", r.ctl.pingCount in 5..7)
        assertEquals("MOVING", r.esp.state) // con PING sigue moviendose
        r.ctl.release() // sin PING
        advanceTimeBy(3_700)
        assertEquals("STOPPED", r.esp.state)
        move.cancel()
    }
}
