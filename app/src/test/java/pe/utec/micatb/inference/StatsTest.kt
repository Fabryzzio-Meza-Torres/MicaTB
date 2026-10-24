package pe.utec.micatb.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    @Test fun medianOfOddAndEven() {
        assertEquals(3.0, Stats.median(listOf(5.0, 1.0, 3.0)), 0.0)
        assertEquals(2.5, Stats.median(listOf(4.0, 1.0, 3.0, 2.0)), 0.0)
    }

    @Test fun percentileUsesNearestRank() {
        val v = (1..100).map { it.toDouble() }
        assertEquals(95.0, Stats.percentile(v, 95.0), 0.0)
        assertEquals(100.0, Stats.percentile(v, 100.0), 0.0)
        assertEquals(1.0, Stats.percentile(v, 0.0), 0.0)
    }

    @Test fun p95OfFiftyRunsPicksTheThirdWorst() {
        val v = (1..50).map { it.toDouble() } // ceil(0,95 * 50) = 48
        assertEquals(48.0, Stats.percentile(v, 95.0), 0.0)
    }

    @Test fun stageCombinesBoth() {
        val s = Stats.stage(listOf(10.0, 20.0, 30.0))
        assertEquals(20.0, s.medianMs, 0.0)
        assertEquals(30.0, s.p95Ms, 0.0)
    }
}
