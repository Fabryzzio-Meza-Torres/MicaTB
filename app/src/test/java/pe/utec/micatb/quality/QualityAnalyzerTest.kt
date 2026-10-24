package pe.utec.micatb.quality

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.utec.micatb.domain.AppConfig
import pe.utec.micatb.inference.PixelRows

class QualityAnalyzerTest {
    private val cfg = AppConfig.parse(File("src/main/assets/app_config.json").readText()).quality

    private class GrayImage(override val width: Int, override val height: Int, val gray: IntArray) : PixelRows {
        override fun readRow(y: Int, out: IntArray) {
            for (x in 0 until width) { val v = gray[y * width + x]; out[x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        }
    }

    private fun noise(w: Int = 640, h: Int = 480): GrayImage {
        val r = Random(7)
        return GrayImage(w, h, IntArray(w * h) { 60 + r.nextInt(120) })
    }

    /** Desenfoque de caja 3x3, repetido [times] veces. */
    private fun blur(img: GrayImage, times: Int): GrayImage {
        var g = img.gray
        repeat(times) {
            val o = IntArray(g.size)
            for (y in 0 until img.height) for (x in 0 until img.width) {
                var s = 0; var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val yy = y + dy; val xx = x + dx
                    if (yy in 0 until img.height && xx in 0 until img.width) { s += g[yy * img.width + xx]; n++ }
                }
                o[y * img.width + x] = s / n
            }
            g = o
        }
        return GrayImage(img.width, img.height, g)
    }

    @Test fun sharpnessDecreasesMonotonicallyWithBlur() {
        val base = noise()
        val scores = (0..4).map { QualityAnalyzer.analyze(blur(base, it), cfg).sharpness }
        for (i in 1 until scores.size) assertTrue("desenfoque $i: ${scores[i]} no baja de ${scores[i - 1]}", scores[i] < scores[i - 1])
    }

    @Test fun flatImageIsFlaggedBlurry() {
        val flat = GrayImage(640, 480, IntArray(640 * 480) { 128 })
        val q = QualityAnalyzer.analyze(flat, cfg)
        assertEquals(0f, q.sharpness, 0f)
        assertEquals(128f, q.brightness, 0.5f)
        assertTrue(QualityFlag.TOO_BLURRY in q.flags)
    }

    @Test fun darkAndBrightImagesAreFlagged() {
        val dark = QualityAnalyzer.analyze(GrayImage(640, 480, IntArray(640 * 480) { 10 }), cfg)
        val white = QualityAnalyzer.analyze(GrayImage(640, 480, IntArray(640 * 480) { 255 }), cfg)
        assertTrue(QualityFlag.TOO_DARK in dark.flags)
        assertTrue(QualityFlag.TOO_BRIGHT in white.flags)
        assertTrue(QualityFlag.CLIPPED in white.flags)
        assertEquals(1f, white.clippedFraction, 0f)
    }

    @Test fun halfWhiteImageHasHalfClipped() {
        val img = GrayImage(640, 480, IntArray(640 * 480) { if (it % 640 < 320) 255 else 100 })
        assertEquals(0.5f, QualityAnalyzer.analyze(img, cfg).clippedFraction, 0.01f)
    }

    @Test fun texturedWellExposedImageIsValid() {
        val q = QualityAnalyzer.analyze(noise(), cfg)
        assertTrue("${q.flags}", q.valid)
    }

    @Test fun largeCameraSizedImagesAreReducedWithoutReadingEverything() {
        val q = QualityAnalyzer.analyze(noise(4000, 3000), cfg)
        assertTrue(q.sharpness > 0f)
    }

    @Test(expected = IllegalArgumentException::class) fun tinyImageIsRejected() {
        QualityAnalyzer.analyze(GrayImage(2, 2, IntArray(4)), cfg)
    }
}
