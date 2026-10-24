package pe.utec.micatb.inference

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.utec.micatb.domain.ModelConfig

/**
 * Paridad de la logica pura contra reference/reference_infer.py, sin celular ni modelo:
 * el preprocesado se compara pixel a pixel con la entrada que Python le dio al modelo, y el
 * postprocesado se corre sobre la salida cruda que el modelo produjo en Python.
 */
class PythonParityTest {
    private val vectors = File("../reference/test_vectors")
    private val config = ModelConfig.parse(File("../models/model_config.json").readText())
    private val expected = JSONObject(File(vectors, "expected.json").readText()).getJSONObject("images")
    private val names = listOf("000087_3_5.jpg", "nocuadrada_0_901021.jpg", "901699.jpg")

    private fun rows(name: String): PixelRows {
        val img = ImageIO.read(File(vectors, name))
        return object : PixelRows {
            override val width = img.width
            override val height = img.height
            override fun readRow(y: Int, out: IntArray) { img.getRGB(0, y, width, 1, out, 0, width) }
        }
    }

    @Test fun preprocessMatchesThePythonInputTensor() {
        for (name in names) {
            val size = config.input.width
            val want = File(vectors, "intermediates/$name.input.u8").readBytes() // HWC RGB uint8
            val got = FloatArray(3 * size * size)
            Preprocess.toTensor(rows(name), config.input, got)

            var worst = 0
            var over = 0
            for (p in 0 until size * size) for (c in 0 until 3) {
                val w = want[p * 3 + c].toInt() and 0xFF
                val g = Math.round(got[c * size * size + p] / config.input.scale)
                val d = abs(w - g)
                if (d > worst) worst = d
                if (d > 2) over++
            }
            // Los decodificadores JPEG pueden diferir en 1 o 2 niveles; el borde y el letterbox, no.
            assertTrue("$name: diferencia maxima $worst", worst <= 4)
            assertTrue("$name: $over pixeles con diferencia > 2", over < size * size * 3 / 1000)
        }
    }

    @Test fun postprocessOfThePythonRawOutputMatchesExpectedJson() {
        for (name in names) {
            val e = expected.getJSONObject(name)
            val raw = FloatArray(config.output.channels * config.output.anchors)
            ByteBuffer.wrap(File(vectors, "intermediates/$name.raw.f32").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer().get(raw)
            val lb = Letterbox.of(e.getInt("width"), e.getInt("height"), config.input.content, config.input.border)

            val got = Postprocess.run(raw, config, lb, e.getInt("width"), e.getInt("height"))
            val want = e.getJSONArray("detections")
            assertEquals("$name: numero de detecciones", want.length(), got.size)
            for (i in 0 until want.length()) {
                val w = want.getJSONObject(i)
                val g = got[i]
                assertEquals("$name[$i] clase", w.getInt("cls"), g.cls)
                assertEquals("$name[$i] puntaje", w.getDouble("score"), g.score.toDouble(), 1e-4)
                val box = w.getJSONArray("box")
                val mine = floatArrayOf(g.x1, g.y1, g.x2, g.y2)
                for (k in 0 until 4) assertEquals("$name[$i] caja[$k]", box.getDouble(k), mine[k].toDouble(), 0.06)
            }
            assertEquals("$name: conteo estricto", e.getInt("count_strict"), got.countFor(config.strict, config.classes))
            assertEquals("$name: conteo amplio", e.getInt("count_broad"), got.countFor(config.broad, config.classes))
        }
    }
}
