package pe.utec.micatb.ui.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.utec.micatb.domain.ModelConfig
import pe.utec.micatb.inference.Detection
import pe.utec.micatb.ui.components.classIsCoccus
import pe.utec.micatb.ui.components.classIsPositive
import pe.utec.micatb.ui.theme.Mica

/**
 * Dibuja las detecciones sobre una imagen mostrada con ajuste "contain". Las contadas llevan llamada
 * numerada; las de otras clases por encima del umbral quedan como filete tenue, sin numero.
 * [scaleBarPx] dibuja una barra de escala solo cuando el campo esta calibrado (largo en pixeles de la imagen).
 */
@Composable
fun DetectionOverlay(
    imageWidth: Int,
    imageHeight: Int,
    counted: List<Detection>,
    others: List<Detection>,
    config: ModelConfig,
    modifier: Modifier = Modifier,
    scaleBar: ScaleBar? = null,
) {
    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelMedium
    val stain = Mica.stain
    val colorsByClass = config.classes.associateWith { n ->
        when {
            n.startsWith("G-") && n.endsWith("bacilli") -> stain.gNegative
            n.startsWith("G-") -> stain.gNegativeSoft
            n.endsWith("bacilli") -> stain.gPositive
            else -> stain.gPositiveSoft
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val scale = minOf(size.width / imageWidth, size.height / imageHeight)
        val ox = (size.width - imageWidth * scale) / 2
        val oy = (size.height - imageHeight * scale) / 2
        val line = 1.5.dp.toPx()

        for (d in others) box(d, config.classes[d.cls], colorsByClass.getValue(config.classes[d.cls]).copy(alpha = 0.55f), line * 0.7f, ox, oy, scale)

        counted.forEachIndexed { i, d ->
            val name = config.classes[d.cls]
            val c = colorsByClass.getValue(name)
            box(d, name, c, line * 1.4f, ox, oy, scale)
            tag(measurer, (i + 1).toString(), c, Offset(ox + d.x1 * scale, oy + d.y1 * scale), labelStyle.fontSize.value)
        }

        scaleBar?.let { sb ->
            val len = sb.imagePx * scale
            val y = oy + imageHeight * scale - 16.dp.toPx()
            val x = ox + 14.dp.toPx()
            drawLine(barColor, Offset(x, y), Offset(x + len, y), strokeWidth = 3.dp.toPx())
            drawLine(barColor, Offset(x, y - 5.dp.toPx()), Offset(x, y + 5.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawLine(barColor, Offset(x + len, y - 5.dp.toPx()), Offset(x + len, y + 5.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            val text = measurer.measure(sb.label, labelStyle.copy(color = barColor))
            drawText(text, topLeft = Offset(x, y - 8.dp.toPx() - text.size.height))
        }
    }
}

/** Barra de escala: largo en pixeles de la imagen original y su rotulo (por ejemplo "20 µm"). */
data class ScaleBar(val imagePx: Float, val label: String)

private fun DrawScope.box(d: Detection, className: String, color: Color, width: Float, ox: Float, oy: Float, scale: Float) {
    val dash = if (classIsPositive(className)) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
    val topLeft = Offset(ox + d.x1 * scale, oy + d.y1 * scale)
    val size = Size((d.x2 - d.x1) * scale, (d.y2 - d.y1) * scale)
    val style = Stroke(width = width, pathEffect = dash)
    if (classIsCoccus(className)) {
        drawOval(color, topLeft, size, style = style)
    } else {
        drawRoundRect(color, topLeft, size, CornerRadius(2.dp.toPx()), style = style)
    }
}

/** Llamada numerada: etiqueta rellena sobre la esquina superior izquierda de la caja. */
private fun DrawScope.tag(measurer: TextMeasurer, text: String, color: Color, at: Offset, fontSizeSp: Float) {
    val label = measurer.measure(text, androidx.compose.ui.text.TextStyle(fontSize = (fontSizeSp - 1).sp, color = Color(0xFF0E0F10), fontFeatureSettings = "tnum"))
    val padX = 4.dp.toPx()
    val padY = 1.dp.toPx()
    val w = label.size.width + 2 * padX
    val h = label.size.height + 2 * padY
    val x = at.x.coerceIn(0f, size.width - w)
    val y = (at.y - h).coerceAtLeast(0f)
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(2.dp.toPx()))
    drawText(label, topLeft = Offset(x + padX, y + padY))
}
