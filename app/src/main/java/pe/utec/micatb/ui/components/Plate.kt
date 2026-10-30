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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.utec.micatb.ui.theme.Mica
import pe.utec.micatb.ui.theme.tabular

/** Aviso permanente: va en todas las pantallas y nunca se oculta. Franja de lavado cian bajo la cabecera. */
@Composable
fun DisclaimerStrip(modelNotice: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Mica.stain.cyanWash)
            .border(BorderStroke(1.dp, Mica.stain.cyanBorder))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            "APOYO AL TAMIZAJE, NO DIAGNÓSTICO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            modelNotice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Marco de lamina: el campo sobre negro con un filete fino. Sin tarjeta ni sombra. */
@Composable
fun PlateFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF050606))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(2.dp)),
    ) { content() }
}

/** Rotulo de instrumento en mayusculas condensadas. */
@Composable
fun Legend(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

/** Par rotulo y valor con cifras tabulares. */
@Composable
fun Reading(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Legend(label)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular, color = valueColor)
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
fun MicaIcon(icon: ImageVector, description: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 24.dp) {
    Icon(icon, description, modifier.size(size), tint)
}

/**
 * Disparador: el unico control primario de la pantalla de captura. Anillo de hueso con nucleo lleno;
 * mientras trabaja, el nucleo se apaga y el anillo queda como indicador.
 */
@Composable
fun ShutterButton(onClick: () -> Unit, enabled: Boolean, busy: Boolean, description: String, modifier: Modifier = Modifier) {
    val ring = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(3.dp, ring, CircleShape)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(if (busy) 28.dp else 60.dp).clip(CircleShape).background(if (busy) Color.Transparent else ring))
    }
}

/** Acciones secundarias del disparador: 56 dp de area tactil, icono y rotulo debajo. */
@Composable
fun SideAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.outline
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier
            .width(72.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MicaIcon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, textAlign = TextAlign.Center)
    }
}

/**
 * Glifo de una clase: la forma dice morfologia (rectangulo = bacilo, elipse = coco), el trazo continuo o
 * discontinuo dice el Gram (continuo = negativo, discontinuo = positivo) y el color lo refuerza.
 */
@Composable
fun ClassGlyph(className: String, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = classColor(className)
    val dash = classIsPositive(className)
    val round = classIsCoccus(className)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = 2.dp.toPx(), pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null)
        val inset = 2.dp.toPx()
        if (round) {
            drawOval(color, Offset(inset, inset), Size(w - 2 * inset, w - 2 * inset), style = stroke)
        } else {
            drawRoundRect(color, Offset(inset, w * 0.28f), Size(w - 2 * inset, w * 0.44f), CornerRadius(2.dp.toPx()), style = stroke)
        }
    }
}

@Composable
fun classColor(className: String): Color = when {
    className.startsWith("G-") && className.endsWith("bacilli") -> Mica.stain.gNegative
    className.startsWith("G-") -> Mica.stain.gNegativeSoft
    className.endsWith("bacilli") -> Mica.stain.gPositive
    else -> Mica.stain.gPositiveSoft
}

fun classIsPositive(className: String) = className.startsWith("G+")

fun classIsCoccus(className: String) = className.endsWith("cocci")

/** Nombre legible: "G-bacilli" -> "Bacilos Gram negativos". */
fun classLabel(className: String): String {
    val gram = if (className.startsWith("G-")) "Gram negativos" else "Gram positivos"
    val shape = if (className.endsWith("bacilli")) "Bacilos" else "Cocos"
    return "$shape $gram"
}
