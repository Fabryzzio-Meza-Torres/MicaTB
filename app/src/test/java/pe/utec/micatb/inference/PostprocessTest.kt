package pe.utec.micatb.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.utec.micatb.domain.ModelConfig
import java.io.File

class PostprocessTest {
    private val config = ModelConfig.parse(File("../models/model_config.json").readText())
    private val a = config.output.anchors
    private val identity = Letterbox(scale = 1.0, newWidth = 352, newHeight = 352, offsetX = 0, offsetY = 0)

    /** Salida [8, anchors] vacia con anclas puestas a mano: (cx, cy, w, h normalizados, clase, puntaje). */
    private fun output(vararg anchors: FloatArray): FloatArray {
        val o = FloatArray(config.output.channels * a)
        anchors.forEachIndexed { i, v ->
            o[i] = v[0]; o[a + i] = v[1]; o[2 * a + i] = v[2]; o[3 * a + i] = v[3]
            o[(4 + v[4].toInt()) * a + i] = v[5]
        }
        return o
    }

    private fun run(o: FloatArray, w: Int = 352, h: Int = 352) = Postprocess.run(o, config, identity, w, h)

    @Test fun overlappingBoxesOfTheSameClassKeepTheBestScore() {
        val d = run(output(floatArrayOf(.5f, .5f, .2f, .2f, 2f, .9f), floatArrayOf(.51f, .5f, .2f, .2f, 2f, .7f)))
        assertEquals(1, d.size)
        assertEquals(.9f, d[0].score, 0f)
    }

    @Test fun overlappingBoxesOfDifferentClassesBothSurvive() {
        val d = run(output(floatArrayOf(.5f, .5f, .2f, .2f, 2f, .9f), floatArrayOf(.5f, .5f, .2f, .2f, 3f, .8f)))
        assertEquals(setOf(2, 3), d.map { it.cls }.toSet())
    }

    @Test fun scoresAtOrBelowBaseConfAreDropped() {
        val d = run(output(floatArrayOf(.5f, .5f, .2f, .2f, 2f, config.post.baseConf), floatArrayOf(.2f, .2f, .1f, .1f, 2f, .26f)))
        assertEquals(1, d.size)
    }

    @Test fun boxesAreClippedToTheImage() {
        val d = run(output(floatArrayOf(.02f, .5f, .2f, .2f, 2f, .9f)), w = 352, h = 352)
        assertEquals(0f, d[0].x1, 0f)
    }

    @Test fun resultIsSortedByScoreAcrossClasses() {
        val d = run(output(floatArrayOf(.2f, .2f, .1f, .1f, 0f, .6f), floatArrayOf(.7f, .7f, .1f, .1f, 2f, .9f), floatArrayOf(.5f, .2f, .1f, .1f, 3f, .8f)))
        assertEquals(listOf(.9f, .8f, .6f), d.map { it.score })
    }

    @Test fun countingAppliesStrictlyGreaterThanAndTheRulesClasses() {
        val d = run(output(floatArrayOf(.2f, .2f, .1f, .1f, 2f, .46f), floatArrayOf(.7f, .7f, .1f, .1f, 2f, .45f), floatArrayOf(.5f, .2f, .1f, .1f, 3f, .8f)))
        assertEquals(1, d.countFor(config.strict, config.classes)) // .45 no supera .45; clase 3 no cuenta
        assertEquals(3, d.countFor(config.broad, config.classes)) // .46 y .45 > .40, y la clase 3 si cuenta
        assertEquals(2, d.countFor(config.strict, config.classes, conf = 0.40f)) // deslizador
    }

    @Test fun thresholdMovesWithoutRerunningTheModel() {
        val d = run(output(floatArrayOf(.2f, .2f, .1f, .1f, 2f, .5f), floatArrayOf(.7f, .7f, .1f, .1f, 2f, .7f)))
        val counts = listOf(0.3f, 0.6f, 0.8f).map { d.countFor(config.strict, config.classes, it) }
        assertEquals(listOf(2, 1, 0), counts)
        assertTrue(d.selectFor(config.strict, config.classes, 0.6f).all { it.score > 0.6f })
    }
}
