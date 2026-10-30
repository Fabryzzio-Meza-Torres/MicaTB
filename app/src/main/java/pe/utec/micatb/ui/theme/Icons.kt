package pe.utec.micatb.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Iconos propios, trazo unico de 1,75 en cuadricula de 24 con extremos redondos. Se tintan con el color
 * del contenido, asi que siguen el esquema claro y oscuro sin variantes.
 */
object MicaIcons {
    private const val STROKE = 1.75f

    private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    private fun ImageVector.Builder.line(d: PathBuilder.() -> Unit) = path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = d,
    )

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }

    /** Portaobjetos con cubreobjetos. */
    val Slide by lazy { icon("Slide") { line { rect(2.5f, 7f, 21.5f, 17f) }; line { rect(9f, 9.5f, 17f, 14.5f) } } }

    /** Visor con circulo central. */
    val Capture by lazy {
        icon("Capture") {
            line { moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f) }
            line { moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f) }
            line { moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f) }
            line { moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f) }
            line { circle(12f, 12f, 3.2f) }
        }
    }

    /** Cuadricula de campos. */
    val Session by lazy {
        icon("Session") {
            line { rect(4f, 4f, 10.5f, 10.5f) }; line { rect(13.5f, 4f, 20f, 10.5f) }
            line { rect(4f, 13.5f, 10.5f, 20f) }; line { rect(13.5f, 13.5f, 20f, 20f) }
        }
    }

    /** Lamina con llamada. */
    val Results by lazy {
        icon("Results") {
            line { rect(3.5f, 3.5f, 20.5f, 20.5f) }
            line { circle(9f, 9f, 2.2f) }
            line { moveTo(10.8f, 10.8f); lineTo(15.5f, 15.5f) }
            line { moveTo(14f, 17f); lineTo(18f, 17f) }
        }
    }

    /** Tres controles deslizantes. */
    val Settings by lazy {
        icon("Settings") {
            line { moveTo(4f, 7f); lineTo(9f, 7f); moveTo(15f, 7f); lineTo(20f, 7f); circle(12f, 7f, 2.5f) }
            line { moveTo(4f, 12f); lineTo(15f, 12f); moveTo(20f, 12f); lineTo(20f, 12f); circle(17.5f, 12f, 2.5f) }
            line { moveTo(4f, 17f); lineTo(6f, 17f); moveTo(12f, 17f); lineTo(20f, 17f); circle(9f, 17f, 2.5f) }
        }
    }

    val Lock by lazy { icon("Lock") { line { rect(5f, 11f, 19f, 20f) }; line { moveTo(8f, 11f); lineTo(8f, 8f); arcToRelative(4f, 4f, 0f, false, true, 8f, 0f); lineTo(16f, 11f) } } }

    val Unlock by lazy { icon("Unlock") { line { rect(5f, 11f, 19f, 20f) }; line { moveTo(8f, 11f); lineTo(8f, 8f); arcToRelative(4f, 4f, 0f, false, true, 7.6f, -1.6f) } } }

    val Warning by lazy {
        icon("Warning") {
            line { moveTo(12f, 3.8f); lineTo(21.2f, 19.8f); lineTo(2.8f, 19.8f); close() }
            line { moveTo(12f, 10f); lineTo(12f, 14.5f); moveTo(12f, 17f); lineTo(12f, 17.1f) }
        }
    }

    val Check by lazy { icon("Check") { line { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } } }

    val Close by lazy { icon("Close") { line { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } } }

    val Back by lazy { icon("Back") { line { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) } } }

    /** Foto de galeria: marco con montana y sol. */
    val Gallery by lazy {
        icon("Gallery") {
            line { rect(3.5f, 4.5f, 20.5f, 19.5f) }
            line { circle(9f, 9.5f, 1.6f) }
            line { moveTo(3.5f, 16.5f); lineTo(9f, 12.5f); lineTo(13f, 15.5f); lineTo(16f, 13f); lineTo(20.5f, 16.5f) }
        }
    }

    val Play by lazy { icon("Play") { line { moveTo(7f, 4.5f); lineTo(19f, 12f); lineTo(7f, 19.5f); close() } } }

    val Pause by lazy { icon("Pause") { line { rect(6.5f, 5f, 10f, 19f) }; line { rect(14f, 5f, 17.5f, 19f) } } }

    val Stop by lazy { icon("Stop") { line { rect(6f, 6f, 18f, 18f) } } }

    val Chevron by lazy { icon("Chevron") { line { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) } } }

    val Trash by lazy {
        icon("Trash") {
            line { moveTo(4.5f, 7f); lineTo(19.5f, 7f); moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f) }
            line { moveTo(6.5f, 7f); lineTo(7.5f, 19.5f); lineTo(16.5f, 19.5f); lineTo(17.5f, 7f) }
        }
    }

    val Info by lazy { icon("Info") { line { circle(12f, 12f, 8.5f) }; line { moveTo(12f, 11f); lineTo(12f, 16.5f); moveTo(12f, 7.7f); lineTo(12f, 7.8f) } } }

    val Home by lazy {
        icon("Home") {
            line { moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f) }
            line { moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f) }
            line { moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f) }
        }
    }

    val Search by lazy { icon("Search") { line { circle(10.5f, 10.5f, 6.5f) }; line { moveTo(15.5f, 15.5f); lineTo(20f, 20f) } } }

    val Export by lazy { icon("Export") { line { moveTo(12f, 15f); lineTo(12f, 4f); moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f) }; line { moveTo(5f, 14f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 14f) } } }
}
