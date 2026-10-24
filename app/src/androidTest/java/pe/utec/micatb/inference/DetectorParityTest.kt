package pe.utec.micatb.inference

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.utec.micatb.domain.ModelConfig

/**
 * Compuerta de paridad (docs/MODEL_CONTRACT.md seccion 6): el detector de la app, con el .tflite real,
 * contra reference/test_vectors/expected.json generado por reference_infer.py.
 * Criterio: mismo conteo estricto y amplio; emparejando cajas de forma voraz por IoU, IoU >= 0,95 y
 * diferencia de puntaje <= 0,01 (en las detecciones por encima del umbral estricto).
 */
class DetectorParityTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val config = instr.targetContext.assets.open("model_config.json").use { ModelConfig.parse(it.readBytes().decodeToString()) }
    private val expected = instr.context.assets.open("expected.json").use { JSONObject(it.readBytes().decodeToString()) }.getJSONObject("images")

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val iw = (minOf(a[2], b[2]) - maxOf(a[0], b[0])).coerceAtLeast(0f)
        val ih = (minOf(a[3], b[3]) - maxOf(a[1], b[1])).coerceAtLeast(0f)
        val inter = iw * ih
        return inter / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter)
    }

    @Test fun appDetectorMatchesPythonReferenceOnAllTestVectors() {
        LiteRtDetector(instr.targetContext, config).use { detector ->
            var worstIou = 1f
            var worstScore = 0f
            var images = 0
            for (name in expected.keys()) {
                val e = expected.getJSONObject(name)
                val bmp = instr.context.assets.open(name).use { BitmapFactory.decodeStream(it) }
                assertEquals("$name ancho", e.getInt("width"), bmp.width)
                val got = detector.detect(bmp).detections

                assertEquals("$name conteo estricto", e.getInt("count_strict"), got.countFor(config.strict, config.classes))
                assertEquals("$name conteo amplio", e.getInt("count_broad"), got.countFor(config.broad, config.classes))

                val want = e.getJSONArray("detections")
                val pool = got.toMutableList()
                for (i in 0 until want.length()) {
                    val w = want.getJSONObject(i)
                    if (w.getDouble("score") <= config.strict.conf) continue
                    val box = w.getJSONArray("box").let { floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat(), it.getDouble(3).toFloat()) }
                    val best = pool.filter { it.cls == w.getInt("cls") }.maxByOrNull { iou(box, floatArrayOf(it.x1, it.y1, it.x2, it.y2)) }
                    assertTrue("$name[$i] sin pareja", best != null)
                    pool.remove(best)
                    worstIou = minOf(worstIou, iou(box, floatArrayOf(best!!.x1, best.y1, best.x2, best.y2)))
                    worstScore = maxOf(worstScore, abs(best.score - w.getDouble("score").toFloat()))
                }
                images++
            }
            Log.i("MicaTB", "paridad: $images imagenes, IoU minimo $worstIou, diferencia maxima de puntaje $worstScore")
            assertTrue("IoU minimo $worstIou < 0,95", worstIou >= 0.95f)
            assertTrue("diferencia de puntaje $worstScore > 0,01", worstScore <= 0.01f)
        }
    }
}
