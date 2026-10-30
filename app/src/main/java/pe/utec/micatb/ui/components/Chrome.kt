package pe.utec.micatb.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.utec.micatb.ui.theme.Mica

private val Navy = Color(0xFF102A43)
private val Teal = Color(0xFF008F91)

/** Marca de la app: retícula teal sobre azul marino con un punto rojo (el bacilo detectado). */
@Composable
fun LogoMark(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clip(RoundedCornerShape(size / 5)).background(Navy)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.width * 0.3f
        val s = Stroke(width = this.size.width * 0.05f)
        drawCircle(Teal, r, c, style = s)
        drawLine(Teal, Offset(c.x, c.y - r * 1.35f), Offset(c.x, c.y + r * 1.35f), s.width)
        drawLine(Teal, Offset(c.x - r * 1.35f, c.y), Offset(c.x + r * 1.35f, c.y), s.width)
        drawCircle(Color(0xFFEF4444), this.size.width * 0.07f, Offset(c.x + r * 0.35f, c.y - r * 0.3f))
    }
}

/** Cabecera de todas las pantallas: marca, sección en mono y el estado real de la app (sin red). */
@Composable
fun AppHeader(section: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LogoMark()
        Column(Modifier.weight(1f)) {
            Text("MicaTB", style = MaterialTheme.typography.headlineMedium)
            Text(section, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusChip("100 % offline")
    }
}

/** Píldora de estado con punto, como "ESP32 Conectado" en Stitch. */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, dot: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Etiqueta de telemetría de 4 dp (identificadores, estados, conteos). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, container: Color = Color(0xFFF1F5F9), content: Color = MaterialTheme.colorScheme.onSurface, dot: Color? = null) {
    Row(
        modifier.clip(MaterialTheme.shapes.small).background(container).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

/** Etiquetas de diagnóstico: rojo para bacilos detectados, ámbar para revisión, verde para limpio. */
@Composable
fun PositiveTag(text: String, modifier: Modifier = Modifier) =
    Tag(text, modifier, Mica.stain.dangerWash, Mica.stain.onDangerWash, dot = Mica.stain.gNegative)

@Composable
fun ReviewTag(text: String, modifier: Modifier = Modifier) = Tag(text, modifier, Mica.stain.warningWash, Mica.stain.warning)

@Composable
fun CleanTag(text: String, modifier: Modifier = Modifier) = Tag(text, modifier, Mica.stain.okWash, Mica.stain.ok)

/** Tarjeta de nivel 1: blanca, filete de 1 dp y sombra ambiente casi nula (plano tonal, sin sombras fuertes). */
@Composable
fun MicaCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border: Color = Color(0xFFE2E8F0),
    shape: Shape = MaterialTheme.shapes.extraLarge,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.clip(shape).background(container, shape).border(BorderStroke(1.dp, border), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) { content() }
}

/** Caja de métrica tintada (lavado cian o rojo) para el tablero de Inicio. */
@Composable
fun MetricBox(label: String, value: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerLow, content: Color = MaterialTheme.colorScheme.onSurface, note: @Composable (() -> Unit)? = null) {
    Column(modifier.clip(MaterialTheme.shapes.extraLarge).background(container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.headlineLarge, color = content)
        note?.invoke()
    }
}
