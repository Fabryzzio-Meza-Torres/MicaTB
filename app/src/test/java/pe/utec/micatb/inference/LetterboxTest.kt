package pe.utec.micatb.inference

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterboxTest {
    private fun lb(w: Int, h: Int) = Letterbox.of(w, h, content = 320, border = 16)

    @Test fun squareImageScalesDirectlyAndOnlyGetsTheBorder() {
        val l = lb(640, 640)
        assertEquals(0.5, l.scale, 0.0)
        assertEquals(320, l.newWidth)
        assertEquals(320, l.newHeight)
        assertEquals(16, l.offsetX)
        assertEquals(16, l.offsetY)
    }

    @Test fun wideImageIsCenteredVertically() {
        val l = lb(640, 480) // dh = 40: top = round(39.9) = 40
        assertEquals(320, l.newWidth)
        assertEquals(240, l.newHeight)
        assertEquals(16, l.offsetX)
        assertEquals(56, l.offsetY)
    }

    @Test fun tallImageIsCenteredHorizontally() {
        val l = lb(480, 640)
        assertEquals(240, l.newWidth)
        assertEquals(320, l.newHeight)
        assertEquals(56, l.offsetX)
        assertEquals(16, l.offsetY)
    }

    @Test fun halfPixelPaddingRoundsDownLikePythonRound() {
        // content 320, nh 317 -> dh = 1.5 -> round(1.4) = 1
        val l = Letterbox.of(1000, 991, content = 320, border = 16)
        assertEquals(1.5, (320 - l.newHeight) / 2.0, 0.0)
        assertEquals(1 + 16, l.offsetY)
    }

    @Test fun cameraPhotoRoundTripIsUnderOnePixel() {
        // 4:3 de 12 MP: ida al espacio del modelo y vuelta a la imagen original.
        val l = lb(4000, 3000)
        for ((x, y) in listOf(0f to 0f, 4000f to 3000f, 1234f to 2345f, 3999f to 1f)) {
            val mx = x * l.scale.toFloat() + l.offsetX
            val my = y * l.scale.toFloat() + l.offsetY
            assertTrue(abs(l.toOriginalX(mx) - x) < 1f)
            assertTrue(abs(l.toOriginalY(my) - y) < 1f)
        }
    }

    @Test(expected = IllegalArgumentException::class) fun emptyImageIsRejected() {
        lb(0, 100)
    }
}
