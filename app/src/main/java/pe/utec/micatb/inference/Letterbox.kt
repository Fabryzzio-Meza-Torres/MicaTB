package pe.utec.micatb.inference

import kotlin.math.min
import kotlin.math.round

/**
 * Geometria del letterbox mas el borde gris de la entrada del modelo.
 * Replica reference/reference_infer.py: `round` de Python redondea la mitad al par, igual que kotlin.math.round.
 */
data class Letterbox(
    /** Factor de escala de la imagen original al contenido (r en la referencia). */
    val scale: Double,
    /** Tamano del contenido reescalado dentro de la entrada. */
    val newWidth: Int,
    val newHeight: Int,
    /** Esquina superior izquierda del contenido dentro de la entrada, borde incluido. */
    val offsetX: Int,
    val offsetY: Int,
) {
    /** Coordenada de la entrada del modelo (px) a la imagen original, sin recortar. */
    fun toOriginalX(x: Float): Float = (x - offsetX) / scale.toFloat()

    fun toOriginalY(y: Float): Float = (y - offsetY) / scale.toFloat()

    companion object {
        fun of(width: Int, height: Int, content: Int, border: Int): Letterbox {
            require(width > 0 && height > 0) { "imagen vacia: ${width}x$height" }
            val r = min(content.toDouble() / height, content.toDouble() / width)
            val nw = round(width * r).toInt()
            val nh = round(height * r).toInt()
            val dw = (content - nw) / 2.0
            val dh = (content - nh) / 2.0
            return Letterbox(r, nw, nh, round(dw - 0.1).toInt() + border, round(dh - 0.1).toInt() + border)
        }
    }
}
