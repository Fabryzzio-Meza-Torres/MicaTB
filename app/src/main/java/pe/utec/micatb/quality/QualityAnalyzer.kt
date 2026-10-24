package pe.utec.micatb.quality

import kotlin.math.max
import pe.utec.micatb.domain.QualityConfig
import pe.utec.micatb.inference.PixelRows

enum class QualityFlag { TOO_BLURRY, TOO_DARK, TOO_BRIGHT, CLIPPED }

/**
 * Metricas de una imagen. [sharpness] es la varianza del Laplaciano sobre gris reducido; su escala depende
 * del contenido y del aumento, por eso el umbral se calibra con el microscopio.
 */
data class QualityReport(
    val sharpness: Float,
    val brightness: Float,
    /** Fraccion de pixeles de gris reducido en el tope del rango (altas luces recortadas). */
    val clippedFraction: Float,
    val flags: List<QualityFlag>,
) {
    val valid: Boolean get() = flags.isEmpty()
}

object QualityAnalyzer {
    private const val CLIP_LEVEL = 250

    fun analyze(src: PixelRows, cfg: QualityConfig): QualityReport {
        val (gray, gw, gh) = reducedGray(src, cfg.analysisSize)
        val sharp = laplacianVariance(gray, gw, gh)

        var sum = 0.0
        var clipped = 0
        for (v in gray) {
            sum += v
            if (v >= CLIP_LEVEL) clipped++
        }
        val brightness = (sum / gray.size).toFloat()
        val clippedFraction = clipped.toFloat() / gray.size

        val flags = buildList {
            if (sharp < cfg.sharpnessMin) add(QualityFlag.TOO_BLURRY)
            if (brightness < cfg.brightnessMin) add(QualityFlag.TOO_DARK)
            if (brightness > cfg.brightnessMax) add(QualityFlag.TOO_BRIGHT)
            if (clippedFraction > cfg.clippedMax) add(QualityFlag.CLIPPED)
        }
        return QualityReport(sharp, brightness, clippedFraction, flags)
    }

    /** Gris por promedio de bloques (sin dependencias), con el lado mayor cerca de [size]. */
    internal fun reducedGray(src: PixelRows, size: Int): Triple<IntArray, Int, Int> {
        val step = max(1, (max(src.width, src.height) + size - 1) / size)
        val gw = src.width / step
        val gh = src.height / step
        require(gw >= 3 && gh >= 3) { "imagen demasiado pequena: ${src.width}x${src.height}" }
        val gray = IntArray(gw * gh)
        val acc = IntArray(gw)
        val row = IntArray(src.width)
        val area = step * step
        for (gy in 0 until gh) {
            acc.fill(0)
            for (sy in 0 until step) {
                src.readRow(gy * step + sy, row)
                for (gx in 0 until gw) {
                    var s = 0
                    for (sx in 0 until step) {
                        val p = row[gx * step + sx]
                        // luma entera 0,299 R + 0,587 G + 0,114 B
                        s += ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                    }
                    acc[gx] += s
                }
            }
            for (gx in 0 until gw) gray[gy * gw + gx] = acc[gx] / area
        }
        return Triple(gray, gw, gh)
    }

    /** Varianza del Laplaciano de 4 vecinos sobre el interior. */
    internal fun laplacianVariance(g: IntArray, w: Int, h: Int): Float {
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val l = g[(y - 1) * w + x] + g[(y + 1) * w + x] + g[y * w + x - 1] + g[y * w + x + 1] - 4 * g[y * w + x]
            sum += l
            sumSq += l.toDouble() * l
            n++
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }
}
