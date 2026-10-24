package pe.utec.micatb.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.utec.micatb.data.FieldOutcome
import pe.utec.micatb.data.FieldPos
import pe.utec.micatb.data.InferenceInput
import pe.utec.micatb.data.PhotoSource
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.data.db.SessionStatus
import pe.utec.micatb.quality.QualityFlag
import pe.utec.micatb.quality.QualityReport

@OptIn(ExperimentalCoroutinesApi::class)
class ScanOrchestratorTest {
    private class FakeFrames : FrameSource {
        var calls = 0
        override suspend fun capture(fieldIdx: Int, attempt: Int): Frame {
            calls++
            return Frame(PhotoSource.Bytes(byteArrayOf(fieldIdx.toByte(), attempt.toByte())), 640, 480)
        }
    }

    /** [bad] decide si la captura (campo, intento, numero de captura de ese campo) sale con mala calidad. */
    private class FakeAnalyzer(val bad: (idx: Int, attempt: Int, nth: Int) -> Boolean = { _, _, _ -> false }) : FieldAnalyzer {
        private val seen = HashMap<Int, Int>()
        override suspend fun quality(frame: Frame): QualityReport {
            val b = (frame.source as PhotoSource.Bytes).bytes
            val idx = b[0].toInt()
            val nth = (seen[idx] ?: 0) + 1
            seen[idx] = nth
            return if (bad(idx, b[1].toInt(), nth)) QualityReport(1f, 120f, 0f, listOf(QualityFlag.TOO_BLURRY)) else QualityReport(200f, 120f, 0f, emptyList())
        }

        override suspend fun infer(frame: Frame) = InferenceInput("m", 0.25f, 0.45f, 0.5f, 3, 4, "[]", 1.0, 2.0, 3.0)
    }

    private class FakeStore : ScanStore {
        val records = ArrayList<Pair<Int, FieldOutcome>>()
        val statuses = ArrayList<SessionStatus>()
        override suspend fun setSessionStatus(status: SessionStatus) { statuses += status }
        override suspend fun record(idx: Int, outcome: FieldOutcome) { records += idx to outcome }
        fun status(idx: Int) = records.filter { it.first == idx }.map { it.second.status }
    }

    private val positions = List(5) { FieldPos(it, it * 100, 0) }

    private class Rig(
        scope: CoroutineScope,
        val motion: SimulatedMotionController,
        val analyzer: FieldAnalyzer,
        policy: RetryPolicy,
        val store: FakeStore = FakeStore(),
        val frames: FakeFrames = FakeFrames(),
    ) {
        val orch = ScanOrchestrator(scope, motion, frames, analyzer, store, ScanConfig(settleMs = 50, maxRetries = 2, onRetriesExhausted = policy))
    }

    private fun TestScope.rig(
        policy: RetryPolicy = RetryPolicy.PAUSE,
        bad: (Int, Int, Int) -> Boolean = { _, _, _ -> false },
        inject: (Int) -> MoveResult.Failed? = { null },
    ) = Rig(this, SimulatedMotionController(100, inject), FakeAnalyzer(bad), policy)

    private fun TestScope.record(r: Rig): MutableList<ScanPhase> {
        val phases = mutableListOf<ScanPhase>()
        backgroundScope.launch { r.orch.state.collect { phases += it.phase } }
        return phases
    }

    // --- serpentin ---

    @Test fun snakePathAlternatesDirectionPerRow() {
        val p = generateSnakePath(cols = 3, rows = 2, stepX = 10, stepY = 20, originX = 5, originY = 7)
        assertEquals(listOf(5 to 7, 15 to 7, 25 to 7, 25 to 27, 15 to 27, 5 to 27), p.map { it.x to it.y })
        assertEquals((0..5).toList(), p.map { it.idx })
    }

    @Test fun snakePathHasNoRepeatedCellsAndEveryStepIsOneCell() {
        val p = generateSnakePath(10, 5, stepX = 30, stepY = 40)
        assertEquals(50, p.size)
        assertEquals(50, p.map { it.x to it.y }.toSet().size)
        for (i in 1 until p.size) {
            val dx = Math.abs(p[i].x - p[i - 1].x); val dy = Math.abs(p[i].y - p[i - 1].y)
            assertTrue("salto de ($dx, $dy) entre ${i - 1} y $i", (dx == 30 && dy == 0) || (dx == 0 && dy == 40))
        }
    }

    // --- flujo normal ---

    @Test fun happyPathVisitsEveryFieldOnceInOrder() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(); val phases = record(r)
        r.orch.start(positions); r.orch.join()
        assertEquals(ScanPhase.COMPLETED, r.orch.state.value.phase)
        assertEquals((0..4).toList(), r.store.records.map { it.first })
        assertTrue(r.store.records.all { it.second.status == FieldStatus.VALID && it.second.attempts == 1 })
        assertEquals(listOf(SessionStatus.RUNNING, SessionStatus.COMPLETED), r.store.statuses)
        assertEquals(5, r.motion.moveCalls)
        for (p in listOf(ScanPhase.MOVING, ScanPhase.SETTLING, ScanPhase.CAPTURING, ScanPhase.QUALITY_CHECK, ScanPhase.INFERRING, ScanPhase.STORING)) assertTrue("falta $p", p in phases)
        assertEquals(5, r.orch.state.value.validFields)
    }

    @Test fun badQualityRetriesAndThenSucceeds() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(bad = { idx, attempt, _ -> idx == 1 && attempt == 1 }); val phases = record(r)
        r.orch.start(positions); r.orch.join()
        assertEquals(2, r.store.records.first { it.first == 1 }.second.attempts)
        assertEquals(FieldStatus.VALID, r.store.records.first { it.first == 1 }.second.status)
        assertTrue(ScanPhase.RETRYING in phases)
    }

    @Test fun exhaustedRetriesWithSkipPolicyMarksTheFieldInvalidAndKeepsItsPhoto() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(policy = RetryPolicy.SKIP, bad = { idx, _, _ -> idx == 2 })
        r.orch.start(positions); r.orch.join()
        val bad = r.store.records.first { it.first == 2 }.second
        assertEquals(FieldStatus.INVALID_QUALITY, bad.status)
        assertEquals(3, bad.attempts) // 1 intento y 2 reintentos
        assertNotNull(bad.photo); assertNull(bad.inference)
        assertEquals(1, r.orch.state.value.invalidFields)
        assertEquals(4, r.orch.state.value.validFields)
        assertEquals(ScanPhase.COMPLETED, r.orch.state.value.phase)
    }

    @Test fun exhaustedRetriesWithPausePolicyWaitsAndResumeStartsAgain() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(policy = RetryPolicy.PAUSE, bad = { idx, _, nth -> idx == 2 && nth <= 3 })
        r.orch.start(positions); advanceUntilIdle()
        val s = r.orch.state.value
        assertEquals(ScanPhase.PAUSED, s.phase); assertEquals(2, s.index); assertTrue(s.canSkip)
        assertEquals(listOf(SessionStatus.RUNNING, SessionStatus.PAUSED), r.store.statuses)
        r.orch.resume(); r.orch.join()
        val f = r.store.records.first { it.first == 2 }.second
        assertEquals(FieldStatus.VALID, f.status); assertEquals(1, f.attempts)
        assertEquals((0..4).toList(), r.store.records.map { it.first })
    }

    @Test fun skipFromAQualityPauseRecordsTheFieldAsInvalid() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(policy = RetryPolicy.PAUSE, bad = { idx, _, _ -> idx == 2 })
        r.orch.start(positions); advanceUntilIdle()
        r.orch.skip(); r.orch.join()
        assertEquals(FieldStatus.INVALID_QUALITY, r.store.records.first { it.first == 2 }.second.status)
        assertEquals(ScanPhase.COMPLETED, r.orch.state.value.phase)
    }

    // --- pausa y cancelacion ---

    @Test fun pauseWaitsForTheFieldInProgressAndDoesNotStartTheNextMove() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.orch.start(positions)
        advanceTimeBy(160) // a mitad del campo 1
        r.orch.pause()
        advanceUntilIdle()
        assertEquals(ScanPhase.PAUSED, r.orch.state.value.phase)
        val doneWhenPaused = r.store.records.map { it.first }
        assertEquals((0..doneWhenPaused.size - 1).toList(), doneWhenPaused) // el campo en curso termino completo
        assertTrue(doneWhenPaused.isNotEmpty() && doneWhenPaused.size < 5)
        val moves = r.motion.moveCalls
        advanceTimeBy(5_000)
        assertEquals(moves, r.motion.moveCalls) // en pausa no hay movimientos nuevos
        r.orch.resume(); r.orch.join()
        assertEquals((0..4).toList(), r.store.records.map { it.first })
    }

    @Test fun cancelSendsStopImmediatelyAndLeavesCancelled() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.orch.start(positions)
        advanceTimeBy(160)
        val before = r.store.records.size
        r.orch.cancel()
        assertEquals(1, r.motion.stopCalls) // STOP inmediato, sin esperar al flujo
        r.orch.join()
        assertEquals(ScanPhase.CANCELLED, r.orch.state.value.phase)
        assertEquals(SessionStatus.CANCELLED, r.store.statuses.last())
        assertEquals(1, r.motion.stopCalls)
        advanceTimeBy(10_000)
        assertTrue(r.store.records.size <= before + 1)
        assertTrue(r.store.records.size < 5)
    }

    // --- fallos de movimiento ---

    @Test fun recoverableMoveFailurePausesAndResumeRetriesTheMove() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(inject = { call -> if (call == 2) MoveResult.Failed("timeout") else null })
        r.orch.start(positions); advanceUntilIdle()
        assertEquals(ScanPhase.PAUSED, r.orch.state.value.phase)
        assertEquals("Movimiento: timeout", r.orch.state.value.message)
        r.orch.resume(); r.orch.join()
        assertEquals(6, r.motion.moveCalls) // 5 campos mas el reintento
        assertEquals((0..4).toList(), r.store.records.map { it.first })
        assertTrue(r.store.records.all { it.second.status == FieldStatus.VALID })
    }

    @Test fun skippingAFailedMoveRecordsFailedMotionAndContinues() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(inject = { call -> if (call == 3) MoveResult.Failed("atascado") else null })
        r.orch.start(positions); advanceUntilIdle()
        r.orch.skip(); r.orch.join()
        assertEquals(listOf(FieldStatus.FAILED_MOTION), r.store.status(2))
        assertEquals(1, r.orch.state.value.failedFields)
        assertEquals(ScanPhase.COMPLETED, r.orch.state.value.phase)
    }

    @Test fun fatalMoveFailureStopsTheSweepAndMarksTheSessionFailed() = runTest(UnconfinedTestDispatcher()) {
        val r = rig(inject = { call -> if (call == 2) MoveResult.Failed("final de carrera", fatal = true) else null })
        r.orch.start(positions); r.orch.join()
        assertEquals(ScanPhase.FAILED, r.orch.state.value.phase)
        assertEquals(SessionStatus.FAILED, r.store.statuses.last())
        assertEquals(1, r.store.records.size)
        assertTrue(r.motion.stopCalls >= 1)
    }

    // --- reanudar y barrido largo ---

    @Test fun startingFromAnIndexOnlyProcessesTheRemainingFields() = runTest(UnconfinedTestDispatcher()) {
        val r = rig()
        r.orch.start(positions, startIndex = 3); r.orch.join()
        assertEquals(listOf(3, 4), r.store.records.map { it.first })
        assertEquals(2, r.motion.moveCalls)
    }

    @Test fun fiftyFieldSweepWithInjectedFailuresHasNoMissingOrDuplicatedFields() = runTest(UnconfinedTestDispatcher()) {
        val path = generateSnakePath(10, 5, 30, 40)
        val r = rig(
            policy = RetryPolicy.PAUSE,
            bad = { idx, attempt, _ -> idx % 11 == 0 && attempt == 1 },          // reintento que lo arregla
            inject = { call -> if (call % 7 == 0) MoveResult.Failed("tiempo agotado") else null }, // pausa recuperable
        )
        backgroundScope.launch { r.orch.state.collect { if (it.phase == ScanPhase.PAUSED && it.canSkip) r.orch.resume() } }
        r.orch.start(path); r.orch.join()
        assertEquals(ScanPhase.COMPLETED, r.orch.state.value.phase)
        assertEquals((0 until 50).toList(), r.store.records.map { it.first }) // ni faltan ni se repiten
        assertTrue(r.store.records.all { it.second.status == FieldStatus.VALID })
        assertTrue(r.motion.moveCalls > 50) // hubo reintentos de movimiento
    }

    // --- movimiento manual ---

    @Test fun manualControllerWaitsForTheOperatorAndStopCancelsThePrompt() = runTest(UnconfinedTestDispatcher()) {
        val m = ManualMotionController()
        var result: MoveResult? = null
        backgroundScope.launch { result = m.moveTo(10, 20) }
        assertEquals(ManualMotionController.Prompt(10, 20), m.prompt.value)
        assertNull(result)
        m.confirm()
        assertEquals(MoveResult.Done(10, 20), result)
        assertNull(m.prompt.value)

        var second: MoveResult? = null
        backgroundScope.launch { second = m.moveTo(1, 2) }
        m.stop()
        assertTrue(second is MoveResult.Failed)
    }
}
