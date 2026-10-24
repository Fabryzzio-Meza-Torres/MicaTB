package pe.utec.micatb.inference

import pe.utec.micatb.domain.CountingRule
import pe.utec.micatb.domain.ModelConfig

/** Deteccion en pixeles de la imagen original. [cls] es el indice en ModelConfig.classes. */
data class Detection(val cls: Int, val score: Float, val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/** Tiempos por etapa en milisegundos. */
data class Timings(val preMs: Double, val inferMs: Double, val postMs: Double)

/**
 * Resultado del modelo tras NMS a base_conf, sin filtrar por el conf final: el deslizador de conf
 * se aplica despues con [filter] y [count] sin volver a ejecutar el modelo.
 */
data class RawDetections(val detections: List<Detection>, val imageWidth: Int, val imageHeight: Int, val timings: Timings)

object Postprocess {
    /**
     * Salida cruda [channels, anchors] (canales primero) a detecciones en la imagen original.
     * Pasos y orden iguales a reference/reference_infer.py: decodificar, NMS por clase, ordenar por
     * puntaje, recortar a max_det, deshacer el letterbox y recortar a la imagen.
     */
    fun run(output: FloatArray, config: ModelConfig, lb: Letterbox, imageWidth: Int, imageHeight: Int): List<Detection> {
        val a = config.output.anchors
        val size = config.input.width.toFloat()
        val nClasses = config.output.channels - 4

        // Decodificar: clase de mayor puntaje por ancla (primera en empate, como argmax) y umbral base.
        val boxes = ArrayList<FloatArray>()
        val scores = ArrayList<Float>()
        val classes = ArrayList<Int>()
        for (i in 0 until a) {
            var best = output[4 * a + i]
            var bestCls = 0
            for (k in 1 until nClasses) {
                val s = output[(4 + k) * a + i]
                if (s > best) { best = s; bestCls = k }
            }
            if (best > config.post.baseConf) {
                val cx = output[i] * size
                val cy = output[a + i] * size
                val w = output[2 * a + i] * size
                val h = output[3 * a + i] * size
                boxes.add(floatArrayOf(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
                scores.add(best)
                classes.add(bestCls)
            }
        }

        val keep = nmsPerClass(boxes, scores, classes, config.post.nmsIou)
        val sorted = keep.sortedByDescending { scores[it] }.take(config.post.maxDet)

        return sorted.map { i ->
            val b = boxes[i]
            Detection(
                cls = classes[i],
                score = scores[i],
                x1 = lb.toOriginalX(b[0]).coerceIn(0f, imageWidth.toFloat()),
                y1 = lb.toOriginalY(b[1]).coerceIn(0f, imageHeight.toFloat()),
                x2 = lb.toOriginalX(b[2]).coerceIn(0f, imageWidth.toFloat()),
                y2 = lb.toOriginalY(b[3]).coerceIn(0f, imageHeight.toFloat()),
            )
        }
    }

    /** NMS voraz independiente por clase: suprime si IoU > [iouThreshold]. Devuelve indices conservados. */
    fun nmsPerClass(boxes: List<FloatArray>, scores: List<Float>, classes: List<Int>, iouThreshold: Float): List<Int> {
        val keep = ArrayList<Int>()
        for (c in classes.distinct().sorted()) {
            var idx = classes.indices.filter { classes[it] == c }.sortedByDescending { scores[it] }
            while (idx.isNotEmpty()) {
                val i = idx[0]
                keep.add(i)
                if (idx.size == 1) break
                idx = idx.drop(1).filter { iou(boxes[i], boxes[it]) <= iouThreshold }
            }
        }
        return keep
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val ix = (minOf(a[2], b[2]) - maxOf(a[0], b[0])).coerceAtLeast(0f)
        val iy = (minOf(a[3], b[3]) - maxOf(a[1], b[1])).coerceAtLeast(0f)
        val inter = ix * iy
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        return inter / (areaA + areaB - inter + 1e-9f)
    }
}

/** Detecciones de las clases de [rule] con puntaje > conf de la regla. */
fun List<Detection>.selectFor(rule: CountingRule, classNames: List<String>, conf: Float = rule.conf): List<Detection> =
    filter { classNames[it.cls] in rule.classes && it.score > conf }

/** Conteo de la regla. [conf] reemplaza el umbral de la regla (deslizador de la interfaz). */
fun List<Detection>.countFor(rule: CountingRule, classNames: List<String>, conf: Float = rule.conf): Int =
    count { classNames[it.cls] in rule.classes && it.score > conf }
