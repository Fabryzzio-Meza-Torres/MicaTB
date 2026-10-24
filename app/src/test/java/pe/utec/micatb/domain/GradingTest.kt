package pe.utec.micatb.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingTest {
    private val app = AppConfig.parse(File("src/main/assets/app_config.json").readText())
    private val min = app.session.minValidFieldsForGrade

    private fun grade(counts: List<Int>) = Grading.grade(app.grading, counts, min)

    private fun graded(counts: List<Int>) = grade(counts) as Grade.Graded

    @Test fun everyBandIsReachable() {
        assertEquals("0", graded(List(min) { 0 }).grade)
        assertEquals("1", graded(List(min) { if (it < 3) 1 else 0 }).grade)   // media 0,3
        assertEquals("2", graded(List(min) { 1 }).grade)                       // media 1
        assertEquals("3", graded(List(min) { 5 }).grade)                       // media 5
        assertEquals("4", graded(List(min) { 40 }).grade)                      // media 40
    }

    @Test fun bandLimitsAreInclusiveOnTop() {
        assertEquals("1", graded(List(min) { if (it < min / 2) 1 else 0 }).grade) // media 0,5
        assertEquals("2", graded(List(min) { 2 }).grade)                          // media 2,0
        assertEquals("3", graded(List(min) { 10 }).grade)                         // media 10,0
    }

    @Test fun tooFewFieldsIsInsufficientNotAGrade() {
        val g = grade(List(min - 1) { 3 })
        assertTrue(g is Grade.Insufficient)
        assertEquals(min - 1, (g as Grade.Insufficient).fields)
    }

    @Test fun resultAlwaysCarriesTheNotDiagnosticLabel() {
        assertEquals("Gradación de demostración, no diagnóstica", graded(List(min) { 1 }).label)
        assertTrue((grade(emptyList()) as Grade.Insufficient).label.contains("no diagnóstica"))
    }

    @Test fun appConfigParsesTheRealFile() {
        assertEquals(512, app.quality.analysisSize)
        assertEquals(800L, app.session.settleMs)
        assertEquals(5, app.grading.bands.size)
    }
}
