package pe.utec.micatb.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pe.utec.micatb.AppContainer
import pe.utec.micatb.inference.BenchmarkResult
import pe.utec.micatb.ui.components.ClassGlyph
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import pe.utec.micatb.ui.components.Legend
import pe.utec.micatb.ui.components.MicaCard
import pe.utec.micatb.ui.components.MicaIcon
import pe.utec.micatb.ui.components.Tag
import pe.utec.micatb.ui.theme.MicaIcons
import pe.utec.micatb.ui.components.MicaButton
import pe.utec.micatb.ui.components.MicaOutlinedButton
import pe.utec.micatb.ui.components.Reading
import pe.utec.micatb.ui.components.classLabel
import pe.utec.micatb.ui.fmt
import pe.utec.micatb.ui.ms
import pe.utec.micatb.ui.theme.Mica
import pe.utec.micatb.ui.theme.tabular

@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bench by vm.bench.collectAsStateWithLifecycle()
    val calib by vm.calib.collectAsStateWithLifecycle()
    val cfg = container.modelConfig

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Configuración del sistema", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                Tag("REV 0.1.0", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text("Modelo de visión, calidad de imagen y almacenamiento sin conexión.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("Motor de IA", MicaIcons.Capture, container.variantName.uppercase()) {
            Text(cfg.modelId, style = MaterialTheme.typography.titleMedium.tabular)
            Text(
                "Huella ${container.activeSha256.take(12)}…${container.activeSha256.takeLast(8)}",
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val variants = listOf("fp16") + cfg.variants.keys
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                variants.forEachIndexed { i, v ->
                    SegmentedButton(
                        selected = ui.variant == v, onClick = { vm.setVariant(v) },
                        shape = SegmentedButtonDefaults.itemShape(i, variants.size, MaterialTheme.shapes.small),
                        modifier = Modifier.height(48.dp),
                        label = { Text(v.uppercase(), style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            Text(
                "FP16 es el predeterminado. INT8 pesa menos; elige con el benchmark si cuenta igual y va más rápido.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cfg.classes.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClassGlyph(name); Text(classLabel(name), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Reading("Estricto", "> ${cfg.strict.conf.fmt()}", Modifier.weight(1f))
                Reading("Amplio", "> ${cfg.broad.conf.fmt()}", Modifier.weight(1f))
                Reading("NMS", cfg.post.nmsIou.fmt(), Modifier.weight(1f))
            }
        }

        Section("Rendimiento", MicaIcons.Play, "${ui.threads} HILOS") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(1, 2, 4).forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = ui.threads == n, onClick = { vm.setThreads(n) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3, MaterialTheme.shapes.small),
                        modifier = Modifier.height(48.dp),
                        label = { Text(if (n == 1) "1 hilo" else "$n hilos", style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            MicaButton(onClick = vm::runBenchmark, Modifier.fillMaxWidth(), enabled = bench !is BenchState.Running) { Text("Medir latencia", style = MaterialTheme.typography.labelLarge) }
            when (val b = bench) {
                is BenchState.Running -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Midiendo ${b.label}: ${b.done} de ${b.total}", style = MaterialTheme.typography.bodyMedium.tabular)
                    LinearProgressIndicator(progress = { b.done.toFloat() / b.total }, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                }
                is BenchState.Done -> BenchmarkTable(b.results, b.file)
                is BenchState.Failed -> Text(b.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                BenchState.Idle -> Text(
                    "5 inferencias de calentamiento y 50 medidas sobre una imagen densa. Para ver el efecto térmico, repite tras 5 minutos de uso continuo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("Calidad de imagen", MicaIcons.Slide, if (ui.calibrated) "CALIBRADO" else "SIN CALIBRAR") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Reading("Nitidez mínima", ui.sharpnessMin.fmt(0), Modifier.weight(1f))
                Reading("Estado", if (ui.calibrated) "Calibrado" else "Inicial", Modifier.weight(1f), if (ui.calibrated) Mica.stain.ok else Mica.stain.warning)
            }
            Text(
                "La nitidez depende del aumento y del contenido. Calíbrala con fotos de tu microscopio: unas enfocadas y unas desenfocadas a propósito.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CalibrationBlock(calib, vm, ui.calibrated)
        }

        Section("Escala del campo", MicaIcons.Session, if (ui.fieldWidthUm != null) "CALIBRADA" else "SIN DATO") {
            var text by remember(ui.fieldWidthUm) { mutableStateOf(ui.fieldWidthUm?.let { if (it % 1f == 0f) it.toInt().toString() else it.fmt(1) }.orEmpty()) }
            OutlinedTextField(
                value = text, onValueChange = { text = it; vm.setFieldWidth(it) },
                label = { Text("Ancho del campo (µm)") },
                supportingText = { Text("Sin este dato no se dibuja barra de escala: no se inventa.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Operación y almacén", MicaIcons.Lock, "100 % OFFLINE") {
            Text("La app no tiene permiso de Internet y funciona sin conexión. Las fotos y los resultados se guardan solo en este teléfono, sin datos de pacientes.", style = MaterialTheme.typography.bodyMedium)
            Text("Versión 0.1.0", style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, tag: String, content: @Composable () -> Unit) {
    MicaCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    MicaIcon(icon, null, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Tag(tag, container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            content()
        }
    }
}

@Composable
private fun BenchmarkTable(results: List<BenchmarkResult>, file: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Legend("Variante", Modifier.weight(0.9f)); Legend("Preproceso", Modifier.weight(1f)); Legend("Inferencia", Modifier.weight(1f)); Legend("Posproceso", Modifier.weight(1f))
        }
        results.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(r.variant.uppercase(), Modifier.weight(0.9f), style = MaterialTheme.typography.titleSmall)
                for (s in listOf(r.pre, r.infer, r.post)) {
                    Column(Modifier.weight(1f)) {
                        Text(s.medianMs.ms(), style = MaterialTheme.typography.titleSmall.tabular)
                        Text("p95 ${s.p95Ms.ms()}", style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val same = results.map { it.countStrict }.distinct().size == 1
        Text(
            if (same) "Mismo conteo en todas las variantes (${results.first().countStrict})." else "El conteo cambia entre variantes: ${results.joinToString { "${it.variant.uppercase()} ${it.countStrict}" }}.",
            style = MaterialTheme.typography.bodyMedium, color = if (same) MaterialTheme.colorScheme.onSurface else Mica.stain.warning,
        )
        Text("Mediana y percentil 95 de 50 medidas. Informe en $file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalibrationBlock(calib: CalibrationState, vm: SettingsViewModel, calibrated: Boolean) {
    val pickSharp = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.addCalibrationPhoto(u, sharp = true) } }
    val pickBlur = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.addCalibrationPhoto(u, sharp = false) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MicaOutlinedButton({ pickSharp.launch("image/*") }, Modifier.weight(1f)) { Text("Foto enfocada", style = MaterialTheme.typography.labelLarge) }
        MicaOutlinedButton({ pickBlur.launch("image/*") }, Modifier.weight(1f)) { Text("Foto desenfocada", style = MaterialTheme.typography.labelLarge) }
    }
    if (calib.sharp.isNotEmpty() || calib.blurred.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Reading("Enfocadas", calib.sharp.joinToString(" · ") { it.fmt(0) }.ifEmpty { "—" }, Modifier.weight(1f))
            Reading("Desenfocadas", calib.blurred.joinToString(" · ") { it.fmt(0) }.ifEmpty { "—" }, Modifier.weight(1f))
        }
        when {
            calib.suggestion != null -> Text("Umbral sugerido: ${calib.suggestion!!.fmt(0)}, entre la peor enfocada y la mejor desenfocada.", style = MaterialTheme.typography.bodyMedium)
            calib.overlaps -> Text("Las desenfocadas puntúan igual o más que las enfocadas: enfoca mejor o cambia las fotos antes de fijar un umbral.", style = MaterialTheme.typography.bodyMedium, color = Mica.stain.warning)
            else -> Text("Añade al menos una foto de cada tipo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MicaButton(vm::applyCalibration, enabled = calib.suggestion != null) { Text("Aplicar umbral", style = MaterialTheme.typography.labelLarge) }
            TextButton(vm::clearCalibration, Modifier.height(56.dp)) { Text("Borrar fotos") }
        }
    }
    if (calibrated) TextButton(vm::resetSharpness, Modifier.height(48.dp)) { Text("Volver al umbral inicial") }
}
