package pe.utec.micatb.ui.results

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.utec.micatb.AppContainer
import pe.utec.micatb.camera.CameraSource
import pe.utec.micatb.data.CountMode
import pe.utec.micatb.data.DetectionsJson
import pe.utec.micatb.data.FieldDetail
import pe.utec.micatb.data.SessionDetail
import pe.utec.micatb.data.SessionSummary
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.data.db.SessionStatus
import pe.utec.micatb.domain.CountingRule
import pe.utec.micatb.domain.Grade
import pe.utec.micatb.domain.Grading
import pe.utec.micatb.inference.Detection
import pe.utec.micatb.inference.countFor
import pe.utec.micatb.inference.selectFor
import pe.utec.micatb.ui.capture.DetectionOverlay
import pe.utec.micatb.ui.components.HairlineDivider
import pe.utec.micatb.ui.components.Legend
import pe.utec.micatb.ui.components.MicaButton
import pe.utec.micatb.ui.components.MicaIcon
import pe.utec.micatb.ui.components.MicaOutlinedButton
import pe.utec.micatb.ui.components.PlateFrame
import pe.utec.micatb.ui.components.Reading
import pe.utec.micatb.ui.components.Segmented
import pe.utec.micatb.ui.fmt
import pe.utec.micatb.ui.pct
import pe.utec.micatb.ui.theme.Mica
import pe.utec.micatb.ui.theme.MicaIcons
import pe.utec.micatb.ui.theme.tabular

private val dateFmt: DateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

private fun statusText(s: SessionStatus) = when (s) {
    SessionStatus.CREATED -> "Creada"
    SessionStatus.RUNNING -> "En curso"
    SessionStatus.PAUSED -> "En pausa"
    SessionStatus.COMPLETED -> "Completa"
    SessionStatus.CANCELLED -> "Cancelada"
    SessionStatus.FAILED -> "Detenida por error"
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clickable(onClick = onBack).semantics { contentDescription = "Volver" }, contentAlignment = Alignment.Center) { MicaIcon(MicaIcons.Back, null) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

// ---------------------------------------------------------------- historial

@Composable
fun HistoryScreen(container: AppContainer, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val sessions by container.sessions.observeSummaries().collectAsStateWithLifecycle(emptyList())
    Column(modifier.fillMaxSize()) {
        Text("Historial", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp))
        if (sessions.isEmpty()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aún no hay sesiones", style = MaterialTheme.typography.titleMedium)
                Text("Cuando termines una sesión de varios campos aparecerá aquí, con sus fotos originales y el conteo de cada campo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sessions, key = { it.id }) { SessionRow(it) { onOpen(it.id) } }
            }
        }
    }
}

@Composable
fun SessionRow(s: SessionSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp)) {
        HairlineDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.label ?: "Sin identificador", style = MaterialTheme.typography.titleMedium)
                Text("${dateFmt.format(Date(s.createdAt))} · ${statusText(s.status)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${s.validFields} de ${s.fields} campos válidos · ${s.countStrict} contados", style = MaterialTheme.typography.bodyMedium.tabular)
            }
            MicaIcon(MicaIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- resumen de sesion

private fun rule(c: AppContainer, mode: CountMode): CountingRule = if (mode == CountMode.STRICT) c.modelConfig.strict else c.modelConfig.broad

private fun FieldDetail.detections(): List<Detection> = inference?.let { DetectionsJson.decode(it.detectionsJson) }.orEmpty()

@Composable
fun SessionResultScreen(container: AppContainer, sessionId: String, onBack: () -> Unit, onOpenField: (Int) -> Unit, modifier: Modifier = Modifier) {
    var reload by remember { mutableIntStateOf(0) }
    val detail by produceState<SessionDetail?>(null, sessionId, reload) { value = container.sessions.detail(sessionId) }
    var mode by remember { mutableStateOf(container.settings.countMode) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val d = detail
    if (d == null) { Column(modifier.fillMaxSize()) { BackHeader("Resultados", onBack) }; return }

    val cfg = container.modelConfig
    val r = rule(container, mode)
    val valid = d.fields.filter { it.field.status == FieldStatus.VALID.name && it.inference != null }
    val counts = valid.map { it.detections().countFor(r, cfg.classes) }
    val total = counts.sum()
    val grade = Grading.grade(container.appConfig.grading, counts, container.appConfig.session.minValidFieldsForGrade)
    var notes by remember(d.session.id) { mutableStateOf(d.session.notes.orEmpty()) }

    LazyVerticalGrid(GridCells.Fixed(3), modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { BackHeader(d.session.sampleLabel ?: "Sesión sin identificador", onBack) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${dateFmt.format(Date(d.session.createdAt))} · ${statusText(SessionStatus.valueOf(d.session.status))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Segmented(listOf(CountMode.STRICT to "Estricto", CountMode.BROAD to "Amplio"), mode, { mode = it; container.settings.countMode = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Reading("Campos válidos", "${valid.size} de ${d.fields.size}", Modifier.weight(1f))
                    Reading("Contados", total.toString(), Modifier.weight(1f))
                    Reading("Por campo", if (valid.isEmpty()) "—" else (total.toDouble() / valid.size).fmt(1), Modifier.weight(1f))
                }
                GradeBlock(grade)
                val invalid = d.fields.count { it.field.status == FieldStatus.INVALID_QUALITY.name }
                if (invalid > 0) Text("$invalid campos sin calidad suficiente no cuentan como examinados.", style = MaterialTheme.typography.bodyMedium, color = Mica.stain.warning)
                HairlineDivider()
                Legend("Campos")
            }
        }
        items(d.fields, key = { it.field.id }) { f -> FieldTile(container, f, r) { onOpenField(f.field.idx) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HairlineDivider()
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                MicaOutlinedButton({ scope.launch { container.sessions.setNotes(sessionId, notes) } }, Modifier.fillMaxWidth()) { Text("Guardar nota", style = MaterialTheme.typography.labelLarge) }
                TextButton({ confirmDelete = true }, Modifier.height(48.dp)) { Text("Eliminar sesión", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("¿Eliminar la sesión?") },
        text = { Text("Se borran sus fotos originales y sus resultados de este teléfono. No se puede deshacer.") },
        confirmButton = { TextButton({ confirmDelete = false; scope.launch { container.sessions.delete(sessionId); reload++; onBack() } }, Modifier.height(48.dp)) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }, Modifier.height(48.dp)) { Text("Conservar") } },
    )
}

@Composable
private fun GradeBlock(g: Grade) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (g) {
            is Grade.Graded -> {
                Text("Grado ${g.grade} · ${g.text}", style = MaterialTheme.typography.titleMedium)
                Text("Promedio ${g.meanPerField.fmt(2)} por campo, sobre ${g.fields} campos válidos.", style = MaterialTheme.typography.bodyMedium.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(g.label, style = MaterialTheme.typography.bodySmall, color = Mica.stain.warning)
            }
            is Grade.Insufficient -> {
                Text("Sin gradación", style = MaterialTheme.typography.titleMedium)
                Text("Hacen falta ${g.needed} campos válidos y hay ${g.fields}.", style = MaterialTheme.typography.bodyMedium.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(g.label, style = MaterialTheme.typography.bodySmall, color = Mica.stain.warning)
            }
        }
    }
}

@Composable
private fun FieldTile(container: AppContainer, f: FieldDetail, rule: CountingRule, onClick: () -> Unit) {
    val cfg = container.modelConfig
    val dets = remember(f.inference?.id) { f.detections() }
    Box(Modifier.aspectRatio(1f).background(Color0).clickable(onClick = onClick).semantics { contentDescription = "Campo ${f.field.idx + 1}" }) {
        if (f.capture != null) FieldImage(container, f, rule, rule.conf, showBoxes = true, maxSide = 360, Modifier.fillMaxSize())
        val label = when (f.field.status) {
            FieldStatus.VALID.name -> "${dets.countFor(rule, cfg.classes)}"
            FieldStatus.PENDING.name -> "—"
            else -> "!"
        }
        Text(
            "${f.field.idx + 1} · $label",
            Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f)).padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium.tabular,
            color = if (f.field.status == FieldStatus.VALID.name) MaterialTheme.colorScheme.onSurface else Mica.stain.warning,
        )
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0xFF050606)

/** Foto original del campo con las cajas de las detecciones guardadas (la foto en disco no tiene cajas). */
@Composable
private fun FieldImage(container: AppContainer, f: FieldDetail, rule: CountingRule, conf: Float, showBoxes: Boolean, maxSide: Int, modifier: Modifier) {
    val capture = f.capture ?: return
    val file: File = container.sessions.photoFile(capture)
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.path, maxSide) {
        value = withContext(Dispatchers.IO) { runCatching { CameraSource.decodeUprightScaled(file, maxSide).asImageBitmap() }.getOrNull() }
    }
    val cfg = container.modelConfig
    val dets = remember(f.inference?.id) { f.detections() }
    Box(modifier) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        if (showBoxes && bmp != null) DetectionOverlay(
            imageWidth = capture.width, imageHeight = capture.height,
            counted = dets.selectFor(rule, cfg.classes, conf),
            others = dets.filter { cfg.classes[it.cls] !in rule.classes && it.score > conf },
            config = cfg,
        )
    }
}

// ---------------------------------------------------------------- detalle de campo

@Composable
fun FieldDetailScreen(container: AppContainer, sessionId: String, idx: Int, onBack: () -> Unit, onGo: (Int) -> Unit, modifier: Modifier = Modifier) {
    val detail by produceState<SessionDetail?>(null, sessionId) { value = container.sessions.detail(sessionId) }
    val d = detail
    if (d == null) { Column(modifier.fillMaxSize()) { BackHeader("Campo ${idx + 1}", onBack) }; return }
    val f = d.fields.firstOrNull { it.field.idx == idx } ?: run { Column(modifier.fillMaxSize()) { BackHeader("Campo ${idx + 1}", onBack) }; return }
    val cfg = container.modelConfig

    var mode by remember(idx) { mutableStateOf(container.settings.countMode) }
    val r = rule(container, mode)
    var conf by remember(idx, mode) { mutableStateOf(r.conf) }
    var showBoxes by remember(idx) { mutableStateOf(true) }
    val dets = remember(f.inference?.id) { f.detections() }
    val q = remember(f.field.qualityJson) { f.field.qualityJson?.let { runCatching { JSONObject(it) }.getOrNull() } }

    val scroll = androidx.compose.foundation.rememberScrollState()
    run {
        Column(modifier.fillMaxSize().verticalScroll(scroll)) {
            BackHeader("Campo ${idx + 1} de ${d.fields.size}", onBack)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val aspect = f.capture?.let { (it.width.toFloat() / it.height).coerceIn(0.6f, 1.8f) } ?: 1f
                PlateFrame(Modifier.fillMaxWidth().aspectRatio(aspect)) { FieldImage(container, f, r, conf, showBoxes, 1600, Modifier.fillMaxSize()) }

                if (f.field.status == FieldStatus.VALID.name) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(dets.countFor(r, cfg.classes, conf).toString(), style = MaterialTheme.typography.displaySmall.tabular)
                        Text("contados", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Segmented(listOf(CountMode.STRICT to "Estricto", CountMode.BROAD to "Amplio"), mode, { mode = it })
                    Row(verticalAlignment = Alignment.CenterVertically) { Legend("Confianza mínima", Modifier.weight(1f)); Text(conf.fmt(), style = MaterialTheme.typography.titleMedium.tabular) }
                    Slider(conf, { conf = it }, valueRange = cfg.post.baseConf..0.95f)
                } else {
                    Text(if (f.field.status == FieldStatus.INVALID_QUALITY.name) "Este campo no tiene calidad suficiente y no cuenta como examinado." else "Este campo no se pudo capturar.", style = MaterialTheme.typography.bodyLarge, color = Mica.stain.warning)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Mostrar cajas", style = MaterialTheme.typography.bodyLarge); Text("Apágalas para ver la foto original sin marcas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(showBoxes, { showBoxes = it })
                }

                if (q != null) {
                    HairlineDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Reading("Nitidez", q.optDouble("sharpness").toFloat().fmt(0), Modifier.weight(1f))
                        Reading("Brillo", q.optDouble("brightness").toFloat().fmt(0), Modifier.weight(1f))
                        Reading("Saturado", q.optDouble("clipped_fraction").toFloat().pct(1), Modifier.weight(1f))
                    }
                    Text("Intentos: ${f.field.attempts}", style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MicaOutlinedButton({ onGo(idx - 1) }, Modifier.weight(1f), enabled = idx > 0) { Text("Anterior", style = MaterialTheme.typography.labelLarge) }
                    MicaOutlinedButton({ onGo(idx + 1) }, Modifier.weight(1f), enabled = idx < d.fields.size - 1) { Text("Siguiente", style = MaterialTheme.typography.labelLarge) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
