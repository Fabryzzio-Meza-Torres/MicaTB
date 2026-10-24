package pe.utec.micatb.inference

import kotlin.math.floor
import kotlin.math.min
import pe.utec.micatb.domain.InputSpec

/** Fuente de pixeles por filas: evita materializar una foto de 12 MP como un IntArray. */
interface PixelRows {
    val width: Int
    val height: Int

    /** Escribe la fila [y] en [out] como ARGB_8888 (width enteros). */
    fun readRow(y: Int, out: IntArray)
}

/**
 * Entrada del modelo: letterbox bilineal, borde gris y tensor NCHW float RGB.
 * El remuestreo reproduce cv2.INTER_LINEAR (centros de pixel a mitad, sin filtro previo al reducir),
 * que es el que usa la referencia en Python.
 */
object Preprocess {
    /** Rellena [out] (3 * height * width) y devuelve la geometria para deshacer el letterbox. */
    fun toTensor(src: PixelRows, spec: InputSpec, out: FloatArray): Letterbox {
        val size = spec.width
        require(spec.height == size && out.size == 3 * size * size) { "tensor de ${out.size} para entrada $size" }
        val lb = Letterbox.of(src.width, src.height, spec.content, spec.border)
        out.fill(spec.padValue * spec.scale)

        val plane = size * size
        val w = src.width
        val h = src.height
        val nw = lb.newWidth
        val nh = lb.newHeight

        // Coeficientes horizontales por columna destino.
        val x0 = IntArray(nw)
        val x1 = IntArray(nw)
        val fx = FloatArray(nw)
        val ratioX = w.toDouble() / nw
        for (dx in 0 until nw) {
            var sx = (dx + 0.5) * ratioX - 0.5
            var ix = floor(sx).toInt()
            var f = (sx - ix).toFloat()
            if (ix < 0) { ix = 0; f = 0f }
            if (ix >= w - 1) { ix = w - 1; f = 0f }
            x0[dx] = ix
            x1[dx] = min(ix + 1, w - 1)
            fx[dx] = f
        }

        val rowA = IntArray(w)
        val rowB = IntArray(w)
        var cachedA = -1
        var cachedB = -1
        val ratioY = h.toDouble() / nh
        for (dy in 0 until nh) {
            val sy = (dy + 0.5) * ratioY - 0.5
            var iy = floor(sy).toInt()
            var fy = (sy - iy).toFloat()
            if (iy < 0) { iy = 0; fy = 0f }
            if (iy >= h - 1) { iy = h - 1; fy = 0f }
            val iy1 = min(iy + 1, h - 1)
            if (iy != cachedA) { src.readRow(iy, rowA); cachedA = iy }
            if (iy1 != cachedB) { src.readRow(iy1, rowB); cachedB = iy1 }

            val rowBase = (lb.offsetY + dy) * size + lb.offsetX
            for (dx in 0 until nw) {
                val a0 = rowA[x0[dx]]
                val a1 = rowA[x1[dx]]
                val b0 = rowB[x0[dx]]
                val b1 = rowB[x1[dx]]
                val f = fx[dx]
                val pos = rowBase + dx
                out[pos] = lerp(a0 shr 16 and 0xFF, a1 shr 16 and 0xFF, b0 shr 16 and 0xFF, b1 shr 16 and 0xFF, f, fy) * spec.scale
                out[plane + pos] = lerp(a0 shr 8 and 0xFF, a1 shr 8 and 0xFF, b0 shr 8 and 0xFF, b1 shr 8 and 0xFF, f, fy) * spec.scale
                out[2 * plane + pos] = lerp(a0 and 0xFF, a1 and 0xFF, b0 and 0xFF, b1 and 0xFF, f, fy) * spec.scale
            }
        }
        return lb
    }

    /** Interpolacion bilineal redondeada a uint8, como el resultado de cv2.resize. */
    private fun lerp(p00: Int, p01: Int, p10: Int, p11: Int, fx: Float, fy: Float): Float {
        val top = p00 + (p01 - p00) * fx
        val bottom = p10 + (p11 - p10) * fx
        val v = top + (bottom - top) * fy
        return floor(v + 0.5f).coerceIn(0f, 255f)
    }
}
