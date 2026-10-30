package pe.utec.micatb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import pe.utec.micatb.AppContainer
import pe.utec.micatb.data.SessionSummary
import pe.utec.micatb.data.db.SessionStatus
import pe.utec.micatb.ui.components.CleanTag
import pe.utec.micatb.ui.components.Legend
import pe.utec.micatb.ui.components.MetricBox
import pe.utec.micatb.ui.components.MicaCard
import pe.utec.micatb.ui.components.MicaIcon
import pe.utec.micatb.ui.components.MicaTonalButton
import pe.utec.micatb.ui.components.PositiveTag
import pe.utec.micatb.ui.components.ReviewTag
import pe.utec.micatb.ui.components.Tag
import pe.utec.micatb.ui.fmt
import pe.utec.micatb.ui.theme.Mica
import pe.utec.micatb.ui.theme.MicaIcons
import pe.utec.micatb.ui.theme.tabular

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
    val min = ((now - ts) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "Ahora"
        min < 60 -> "Hace $min min"
        min < 24 * 60 -> "Hace ${min / 60} h"
        else -> "Hace ${min / (24 * 60)} d"
    }
}

@Composable
fun HomeScreen(container: AppContainer, onNavigate: (String) -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val cfg = container.modelConfig
    val sessions by container.sessions.observeSummaries().collectAsStateWithLifecycle(emptyList())
    val unfinished by produceState(0, sessions) { value = container.sessions.unfinished().size }

    val today = sessions.filter { it.createdAt >= startOfToday() }
    val withBacilli = today.count { it.countStrict > 0 }
    val rate = if (today.isEmpty()) null else withBacilli * 100f / today.size
    val durations = sessions.filter { it.status == SessionStatus.COMPLETED && it.finishedAt != null }.map { (it.finishedAt!! - it.createdAt) / 60_000.0 }
    val last = sessions.firstOrNull()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Modelo activo (en Stitch: laboratorio y estación). Aquí van el modelo y su variante, que sí existen.
        MicaCard(container = Mica.stain.cyanWash, border = Mica.stain.cyanBorder) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Modelo activo", style = MaterialTheme.typography.titleLarge)
                    Text(cfg.modelId, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Huella ${container.activeSha256.take(8)}…${container.activeSha256.takeLast(6)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tag(container.variantName.uppercase(), container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // Tarjeta principal: iniciar un barrido.
        Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.primary)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.clip(MaterialTheme.shapes.small).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.inversePrimary))
                    Text(if (unfinished > 0) "Sesión sin terminar" else "Listo para escanear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
                Text("Escaneo automatizado", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                Text("Microscopía con IA y barrido en ${container.appConfig.session.defaultFields} campos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(MaterialTheme.shapes.medium).background(Color.White).clickable { onNavigate("session") },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (unfinished > 0) "Reanudar sesión" else "Iniciar análisis", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    Box(
                        Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.16f)).clickable { onNavigate("capture") }
                            .semantics { contentDescription = "Capturar un campo suelto" },
                        contentAlignment = Alignment.Center,
                    ) { MicaIcon(MicaIcons.Capture, null, tint = Color.White) }
                }
            }
        }

        // Métricas del día, calculadas de las sesiones guardadas.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox("Muestras hoy", today.size.toString().padStart(2, '0'), Modifier.weight(1f).fillMaxSize())
            MetricBox(
                "Con bacilos", withBacilli.toString().padStart(2, '0'), Modifier.weight(1f).fillMaxSize(),
                container = Mica.stain.dangerWash, content = Mica.stain.onDangerWash,
            ) { rate?.let { Text("${it.fmt(1)} % de hoy", style = MaterialTheme.typography.labelSmall.tabular, color = Mica.stain.onDangerWash) } }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox(
                "Tiempo promedio", if (durations.isEmpty()) "—" else durations.average().fmt(1), Modifier.weight(1f).fillMaxSize(),
            ) { Text(if (durations.isEmpty()) "sin sesiones completas" else "min por sesión", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            MetricBox("Calidad de imagen", if (container.appConfig.quality.calibrated) "Calibrada" else "Inicial", Modifier.weight(1f).fillMaxSize()) {
                if (container.appConfig.quality.calibrated) CleanTag("CON MICROSCOPIO") else ReviewTag("SIN CALIBRAR")
            }
        }

        if (unfinished > 0) {
            MicaCard(container = Mica.stain.warningWash, border = Color(0xFFFDE68A), onClick = { onNavigate("session") }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (unfinished == 1) "Hay una sesión sin terminar" else "Hay $unfinished sesiones sin terminar", style = MaterialTheme.typography.titleMedium, color = Mica.stain.warning)
                    Text("Reanúdala desde el primer campo que no tiene resultado.", style = MaterialTheme.typography.bodySmall, color = Mica.stain.warning)
                }
            }
        }

        LastScreening(last, onOpen)
    }
}

@Composable
private fun LastScreening(s: SessionSummary?, onOpen: (String) -> Unit) {
    MicaCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicaIcon(MicaIcons.Results, null, tint = MaterialTheme.colorScheme.primary)
                Text("Último tamizaje", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (s != null) Text(ago(s.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s == null) {
                Legend("Aún no hay sesiones")
                Text("Cuando termines un barrido aparecerá aquí con sus conteos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.label ?: "Sin identificador", style = MaterialTheme.typography.labelLarge.copy(fontFamily = pe.utec.micatb.ui.theme.JetBrainsMono))
                            if (s.countStrict > 0) PositiveTag("${s.countStrict} bacilos") else CleanTag("SIN BACILOS")
                        }
                        Text("${s.validFields} de ${s.fields} campos válidos", style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MicaTonalButton({ onOpen(s.id) }) { Text("Detalles", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
