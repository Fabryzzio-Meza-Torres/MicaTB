package pe.utec.micatb.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ceil
import kotlin.math.sqrt
import org.json.JSONObject
import pe.utec.micatb.AppContainer
import pe.utec.micatb.data.SessionDetail
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.inference.countFor
import pe.utec.micatb.ui.components.CleanTag
import pe.utec.micatb.ui.components.Legend
import pe.utec.micatb.ui.components.MicaButton
import pe.utec.micatb.ui.components.MicaCard
import pe.utec.micatb.ui.components.MicaIcon
import pe.utec.micatb.ui.components.MicaOutlinedButton
import pe.utec.micatb.ui.components.PositiveTag
import pe.utec.micatb.ui.components.ReviewTag
import pe.utec.micatb.ui.components.Tag
import pe.utec.micatb.ui.fmt
import pe.utec.micatb.ui.theme.Mica
import pe.utec.micatb.ui.theme.MicaIcons
import pe.utec.micatb.ui.theme.tabular

private enum class Filter(val label: String) { ALL("Todos"), POSITIVE("Con bacilos"), CLEAN("Sin hallazgos"), REVIEW("Por revisar") }

private class FieldRow(val idx: Int, val count: Int, val status: FieldStatus, val sharpness: Double?, val f: pe.utec.micatb.data.FieldDetail)

/** Galería de campos de la última sesión (Stitch "Galería de campos"): matriz de rastreo, filtros y tarjetas. */
@Composable
fun GalleryScreen(container: AppContainer, onOpenField: (String, Int) -> Unit, onOpenSession: (String) -> Unit, onNewScan: () -> Unit, modifier: Modifier = Modifier) {
    val sessions by container.sessions.observeSummaries().collectAsStateWithLifecycle(emptyList())
    val last = sessions.firstOrNull()
    if (last == null) {
        EmptyState("Aún no hay campos", "Haz un barrido en Escaneos y cada campo aparecerá aquí con sus cajas de detección.", "Ir a Escaneos", onNewScan, modifier)
        return
    }
    val detail by produceState<SessionDetail?>(null, last.id, last.validFields, last.fields) { value = container.sessions.detail(last.id) }
    val d = detail ?: return
    val cfg = container.modelConfig
    val mode = container.settings.countMode
    val r = rule(container, mode)
    val rows = remember(d, mode) {
        d.fields.map { f ->
            val status = FieldStatus.valueOf(f.field.status)
            val n = if (status == FieldStatus.VALID && f.inference != null) f.detections().countFor(r, cfg.classes) else 0
            val sharp = f.field.qualityJson?.let { runCatching { JSONObject(it).optDouble("sharpness") }.getOrNull() }
            FieldRow(f.field.idx, n, status, sharp, f)
        }
    }
    var filter by remember { mutableStateOf(Filter.ALL) }
    val shown = rows.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.POSITIVE -> it.count > 0
            Filter.CLEAN -> it.status == FieldStatus.VALID && it.count == 0
            Filter.REVIEW -> it.status != FieldStatus.VALID
        }
    }
    val positives = rows.count { it.count > 0 }
    fun countOf(f: Filter) = rows.count {
        when (f) {
            Filter.ALL -> true
            Filter.POSITIVE -> it.count > 0
            Filter.CLEAN -> it.status == FieldStatus.VALID && it.count == 0
            Filter.REVIEW -> it.status != FieldStatus.VALID
        }
    }

    LazyVerticalGrid(
        GridCells.Fixed(2), modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MicaCard(container = Mica.stain.cyanWash, border = Mica.stain.cyanBorder, onClick = { onOpenSession(last.id) }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(last.label ?: "Sin identificador", style = MaterialTheme.typography.labelLarge.copy(fontFamily = pe.utec.micatb.ui.theme.JetBrainsMono))
                        Text("${last.validFields} de ${last.fields} campos válidos · ${if (mode == pe.utec.micatb.data.CountMode.STRICT) "conteo estricto" else "conteo amplio"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MicaIcon(MicaIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { TrackingMatrix(rows, positives) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { fl ->
                    val on = fl == filter
                    val positive = fl == Filter.POSITIVE
                    Box(
                        Modifier.clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else if (positive) Mica.stain.dangerWash else MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { filter = fl }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("${fl.label} (${countOf(fl)})", style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onPrimary else if (positive) Mica.stain.onDangerWash else MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        items(shown, key = { it.f.field.id }) { row -> FieldCard(container, row, r) { onOpenField(last.id, row.idx) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            MicaButton({ onOpenSession(last.id) }, Modifier.fillMaxWidth()) { Text("Abrir informe de la sesión", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Cuadrícula de campos de la sesión; los campos con bacilos contados van en rojo (Stitch: "Matriz de Rastreo"). */
@Composable
private fun TrackingMatrix(rows: List<FieldRow>, positives: Int) {
    val cols = ceil(sqrt(rows.size.toDouble())).toInt().coerceAtLeast(1)
    val totalCount = rows.sumOf { it.count }
    MicaCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicaIcon(MicaIcons.Session, null, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
                Text("Matriz de rastreo ${cols}×${ceil(rows.size / cols.toDouble()).toInt()}", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                Text("${rows.size} campos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.size(120.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.chunked(cols).forEach { line ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            line.forEach { c ->
                                Box(Modifier.weight(1f).fillMaxSize().background(
                                    when {
                                        c.count > 0 -> Mica.stain.gNegative
                                        c.status != FieldStatus.VALID -> Mica.stain.warningWash
                                        else -> MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                ))
                            }
                            repeat(cols - line.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MatrixLine("Campos con bacilos", "$positives", Mica.stain.onDangerWash)
                    MatrixLine("Bacilos contados", "$totalCount", MaterialTheme.colorScheme.onSurface)
                    MatrixLine("Por campo", if (rows.isEmpty()) "—" else (totalCount.toDouble() / rows.size).fmt(1), MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun MatrixLine(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFamily = pe.utec.micatb.ui.theme.JetBrainsMono).tabular, color = valueColor)
    }
}

@Composable
private fun FieldCard(container: AppContainer, row: FieldRow, rule: pe.utec.micatb.domain.CountingRule, onClick: () -> Unit) {
    MicaCard(Modifier.semantics { contentDescription = "Campo ${row.idx + 1}" }, onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF050606))) {
                if (row.f.capture != null) FieldImage(container, row.f, rule, rule.conf, showBoxes = true, maxSide = 480, Modifier.fillMaxSize())
                Tag("F${(row.idx + 1).toString().padStart(3, '0')}", Modifier.align(Alignment.TopStart).padding(8.dp), container = Color(0xFF16334A), content = Color.White)
                Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    when {
                        row.status != FieldStatus.VALID -> ReviewTag(if (row.status == FieldStatus.INVALID_QUALITY) "REVISAR" else "SIN CAPTURA")
                        row.count > 0 -> PositiveTag("+${row.count} ${if (row.count == 1) "BACILO" else "BACILOS"}")
                        else -> CleanTag("NEGATIVO")
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("Campo ${row.idx + 1}", style = MaterialTheme.typography.titleSmall)
                Text(row.sharpness?.let { "Nitidez ${it.fmt(0)}" } ?: "Sin análisis", style = MaterialTheme.typography.labelMedium.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun EmptyState(title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Legend("Sin datos")
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MicaOutlinedButton(onAction) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}
