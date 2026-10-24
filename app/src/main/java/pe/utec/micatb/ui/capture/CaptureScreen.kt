package pe.utec.micatb.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.utec.micatb.AppContainer
import pe.utec.micatb.data.CountMode
import pe.utec.micatb.inference.countFor
import pe.utec.micatb.inference.selectFor
import pe.utec.micatb.quality.QualityFlag
import pe.utec.micatb.quality.QualityReport
import pe.utec.micatb.ui.components.CameraPreviewView
import pe.utec.micatb.ui.components.ClassGlyph
import pe.utec.micatb.ui.components.HairlineDivider
import pe.utec.micatb.ui.components.MicaButton
import pe.utec.micatb.ui.components.MicaOutlinedButton
import pe.utec.micatb.ui.components.Legend
import pe.utec.micatb.ui.components.MicaIcon
import pe.utec.micatb.ui.components.PlateFrame
import pe.utec.micatb.ui.components.Reading
import pe.utec.micatb.ui.components.ShutterButton
import pe.utec.micatb.ui.components.SideAction
import pe.utec.micatb.ui.components.classLabel
import pe.utec.micatb.ui.fmt
import pe.utec.micatb.ui.ms
import pe.utec.micatb.ui.pct
import pe.utec.micatb.ui.theme.MicaIcons
import pe.utec.micatb.ui.theme.Mica
import pe.utec.micatb.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(vm: CaptureViewModel, container: AppContainer, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locked by vm.camera.locked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::loadUri) }
    var showSources by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(4.dp))
            PlateSection(vm, container, state, hasCamera, onAskCamera = { askCamera.launch(Manifest.permission.CAMERA) })

            state.error?.let { ErrorBar(it, onDismiss = vm::dismissError) }

            val analysis = state.analysis
            if (state.phase == Phase.RESULT && analysis != null) {
                CountLegend(container, state, analysis.raw.detections, vm.activeRule().classes)
                ThresholdControls(container, state, onMode = vm::setMode, onConf = vm::setConf)
                QualitySection(analysis.quality, container.settings.effectiveQuality(container.appConfig.quality).calibrated)
                Legend(
                    "Preproceso ${analysis.raw.timings.preMs.ms()} · inferencia ${analysis.raw.timings.inferMs.ms()} · posproceso ${analysis.raw.timings.postMs.ms()}",
                    Modifier.padding(bottom = 8.dp),
                )
            } else if (state.phase == Phase.CAMERA) {
                CaptureHint(hasCamera, locked)
            }
            Spacer(Modifier.height(8.dp))
        }

        HairlineDivider()
        ActionBar(
            state = state,
            locked = locked,
            canShoot = hasCamera,
            onShoot = vm::capture,
            onSources = { showSources = true },
            onLock = { if (locked) vm.camera.unlock() else vm.camera.lock() },
            onRetake = vm::retake,
            onSave = vm::save,
        )
    }

    if (showSources) {
        ModalBottomSheet(onDismissRequest = { showSources = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Analizar una imagen", style = MaterialTheme.typography.titleLarge)
                Text("Muestras del conjunto público (CC BY 4.0) o una foto de la galería.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                vm.samples.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clickable { showSources = false; vm.loadSample(name) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(name, style = MaterialTheme.typography.bodyLarge.tabular) }
                    HairlineDivider()
                }
                Spacer(Modifier.height(8.dp))
                MicaOutlinedButton(
                    onClick = { showSources = false; pickImage.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Elegir de la galería") }
            }
        }
    }
}

@Composable
private fun PlateSection(vm: CaptureViewModel, container: AppContainer, state: CaptureUiState, hasCamera: Boolean, onAskCamera: () -> Unit) {
    val bmp = state.bitmap
    val aspect = if (state.phase == Phase.CAMERA || bmp == null) 3f / 4f else (bmp.width.toFloat() / bmp.height).coerceIn(0.6f, 1.8f)
    PlateFrame(Modifier.fillMaxWidth().aspectRatio(aspect)) {
        when {
            state.phase == Phase.CAMERA && !hasCamera -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MicaIcon(MicaIcons.Capture, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp)
                Spacer(Modifier.height(12.dp))
                Text("La cámara necesita permiso", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Se usa solo para capturar el campo del microscopio. También puedes analizar una imagen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                MicaButton(onClick = onAskCamera, Modifier.height(48.dp)) { Text("Permitir cámara") }
            }

            state.phase == Phase.CAMERA -> CameraPreviewView(vm.camera)

            bmp != null -> {
                Image(bmp.asImageBitmap(), "Campo analizado", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                val analysis = state.analysis
                if (analysis != null) {
                    val rule = vm.activeRule()
                    val dets = analysis.raw.detections
                    DetectionOverlay(
                        imageWidth = bmp.width, imageHeight = bmp.height,
                        counted = dets.selectFor(rule, container.modelConfig.classes, state.conf),
                        others = dets.filter { container.modelConfig.classes[it.cls] !in rule.classes && it.score > state.conf },
                        config = container.modelConfig,
                        scaleBar = container.settings.fieldWidthUm?.let { um -> scaleBarFor(bmp.width, um) },
                    )
                }
                if (state.phase == Phase.WORKING) WorkingBar()
            }
        }
        if (state.phase == Phase.WORKING) WorkingBar()
    }
}

private fun scaleBarFor(imageWidth: Int, fieldWidthUm: Float): ScaleBar {
    // Largo de barra redondeado a 1, 2 o 5 por potencia de diez, cerca de un quinto del campo.
    val target = fieldWidthUm / 5f
    val pow = Math.pow(10.0, Math.floor(Math.log10(target.toDouble()))).toFloat()
    val um = listOf(1f, 2f, 5f, 10f).map { it * pow }.last { it <= target }
    return ScaleBar(imagePx = um / fieldWidthUm * imageWidth, label = "${um.fmt(if (um < 1) 1 else 0)} µm")
}

@Composable
private fun WorkingBar() {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)), verticalArrangement = Arrangement.Bottom) {
        Text("Analizando campo…", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicaIcon(MicaIcons.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(message, Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        TextButton(onClick = onDismiss, Modifier.height(48.dp)) { Text("Cerrar", color = MaterialTheme.colorScheme.onErrorContainer) }
    }
}

@Composable
private fun CaptureHint(hasCamera: Boolean, locked: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Captura un campo", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                !hasCamera -> "Sin cámara puedes analizar una imagen de muestra o de la galería."
                locked -> "Exposición y balance de blancos bloqueados. Toca la imagen para reenfocar y captura."
                else -> "Toca la imagen para enfocar, bloquea exposición y balance de blancos, y captura."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Leyenda de figura: define que se conto y con que umbral, y da el numero. */
@Composable
private fun CountLegend(container: AppContainer, state: CaptureUiState, dets: List<pe.utec.micatb.inference.Detection>, ruleClasses: List<String>) {
    val cfg = container.modelConfig
    val rule = if (state.mode == CountMode.STRICT) cfg.strict else cfg.broad
    val n = dets.countFor(rule, cfg.classes, state.conf)
    val other = if (state.mode == CountMode.STRICT) cfg.broad else cfg.strict
    val otherN = dets.countFor(other, cfg.classes, other.conf)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Legend("Campo · ${state.sourceLabel}")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(n.toString(), style = MaterialTheme.typography.displaySmall.tabular, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (n == 1) "contado" else "contados",
                Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ruleClasses.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClassGlyph(name)
                Text(classLabel(name), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Puntaje mayor que ${state.conf.fmt()}. Con la otra definición se contarían $otherN.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThresholdControls(container: AppContainer, state: CaptureUiState, onMode: (CountMode) -> Unit, onConf: (Float) -> Unit) {
    val cfg = container.modelConfig
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(CountMode.STRICT to "Estricto", CountMode.BROAD to "Amplio").forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2, MaterialTheme.shapes.small),
                    modifier = Modifier.height(48.dp),
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        Text(
            if (state.mode == CountMode.STRICT) "Estricto cuenta solo ${classLabel(cfg.strict.classes.first()).replaceFirstChar { it.lowercase() }}." else "Amplio suma los bacilos Gram negativos y positivos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend("Confianza mínima", Modifier.weight(1f))
            Text(state.conf.fmt(), style = MaterialTheme.typography.titleMedium.tabular)
        }
        Slider(
            value = state.conf,
            onValueChange = onConf,
            valueRange = cfg.post.baseConf..0.95f,
            modifier = Modifier.semantics { contentDescription = "Confianza mínima ${state.conf.fmt()}" },
        )
    }
}

@Composable
private fun QualitySection(q: QualityReport, calibrated: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HairlineDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Reading("Nitidez", q.sharpness.fmt(0), Modifier.weight(1f), flagColor(QualityFlag.TOO_BLURRY in q.flags))
            Reading("Brillo", q.brightness.fmt(0), Modifier.weight(1f), flagColor(QualityFlag.TOO_DARK in q.flags || QualityFlag.TOO_BRIGHT in q.flags))
            Reading("Saturado", q.clippedFraction.pct(1), Modifier.weight(1f), flagColor(QualityFlag.CLIPPED in q.flags))
        }
        val ok = q.valid
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MicaIcon(if (ok) MicaIcons.Check else MicaIcons.Warning, null, tint = if (ok) Mica.stain.ok else Mica.stain.warning, size = 20.dp)
            Text(
                if (ok) "Imagen válida para contar" else q.flags.joinToString(" · ") { flagText(it) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onSurface else Mica.stain.warning,
            )
        }
        if (!calibrated) {
            Text(
                "Umbrales de calidad sin calibrar con el microscopio. Calíbralos en Ajustes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun flagColor(bad: Boolean) = if (bad) Mica.stain.warning else MaterialTheme.colorScheme.onSurface

private fun flagText(f: QualityFlag) = when (f) {
    QualityFlag.TOO_BLURRY -> "Borrosa: enfoca de nuevo"
    QualityFlag.TOO_DARK -> "Poca luz: sube la iluminación"
    QualityFlag.TOO_BRIGHT -> "Demasiada luz: baja la iluminación"
    QualityFlag.CLIPPED -> "Zonas quemadas: baja la exposición"
}

@Composable
private fun ActionBar(
    state: CaptureUiState, locked: Boolean, canShoot: Boolean,
    onShoot: () -> Unit, onSources: () -> Unit, onLock: () -> Unit, onRetake: () -> Unit, onSave: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (state.phase == Phase.RESULT) {
            MicaOutlinedButton(onClick = onRetake, Modifier.height(56.dp).weight(1f)) { Text("Nueva captura", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.size(12.dp))
            MicaButton(onClick = onSave, enabled = state.savedAs == null, modifier = Modifier.height(56.dp).weight(1f)) {
                Text(if (state.savedAs == null) "Guardar campo" else "Guardado", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            SideAction(MicaIcons.Gallery, "Imagen", onSources)
            ShutterButton(onShoot, enabled = canShoot && state.phase == Phase.CAMERA, busy = state.phase == Phase.WORKING, description = "Capturar campo")
            SideAction(if (locked) MicaIcons.Lock else MicaIcons.Unlock, if (locked) "Bloqueado" else "Bloquear", onLock, active = locked, enabled = canShoot)
        }
    }
}
