package pe.utec.micatb.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.DateFormat
import java.util.Date
import pe.utec.micatb.AppContainer
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.scan.ScanPhase
import pe.utec.micatb.scan.ScanState
import pe.utec.micatb.ui.components.CameraPreviewView
import pe.utec.micatb.ui.components.HairlineDivider
import pe.utec.micatb.ui.components.Legend
import pe.utec.micatb.ui.components.MicaButton
import pe.utec.micatb.ui.components.MicaIcon
import pe.utec.micatb.ui.components.MicaOutlinedButton
import pe.utec.micatb.ui.components.PlateFrame
import pe.utec.micatb.ui.components.Reading
import pe.utec.micatb.ui.components.Segmented
import pe.utec.micatb.ui.theme.Mica
import pe.utec.micatb.ui.theme.MicaIcons
import pe.utec.micatb.ui.theme.tabular

@Composable
fun SessionScreen(container: AppContainer, onOpenResults: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm: SessionViewModel = viewModel(factory = viewModelFactory { initializer { SessionViewModel(container) } })
    val run by vm.run.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        error?.let { Message(it, onDismiss = vm::dismissError, modifier = Modifier.padding(16.dp)) }
        val r = run
        if (r == null) SetupPanel(vm) else ActivePanel(vm, r, onOpenResults)
    }
}

@Composable
private fun SetupPanel(vm: SessionViewModel) {
    val setup by vm.setup.collectAsStateWithLifecycle()
    val unfinished by vm.unfinished.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, java.util.Locale.forLanguageTag("es")) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Nueva sesión", style = MaterialTheme.typography.headlineSmall)

        if (unfinished.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend("Sesiones sin terminar")
                unfinished.forEach { u ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HairlineDivider()
                        Text(u.label ?: "Sin identificador", style = MaterialTheme.typography.titleMedium)
                        Text("${df.format(Date(u.createdAt))} · ${u.done} de ${u.total} campos", style = MaterialTheme.typography.bodyMedium.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MicaButton({ vm.resumeSession(u.id) }) { Text("Reanudar", style = MaterialTheme.typography.labelLarge) }
                            TextButton({ vm.discardSession(u.id) }, Modifier.height(56.dp)) { Text("Descartar") }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = setup.label, onValueChange = vm::setLabel, singleLine = true,
            label = { Text("Identificador de muestra (opcional)") },
            supportingText = { Text("Texto libre y local. No escribas datos de pacientes.") },
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Legend("Número de campos")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MicaOutlinedButton({ vm.setFields(setup.fields - 1) }, Modifier.size(56.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(setup.fields.toString(), style = MaterialTheme.typography.headlineMedium.tabular, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                MicaOutlinedButton({ vm.setFields(setup.fields + 1) }, Modifier.size(56.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Legend("Movimiento de la lámina")
            Segmented(listOf(MotionMode.MANUAL to "Manual", MotionMode.SIMULATED to "Simulado", MotionMode.BLE_SIM to "BLE sim."), setup.motion, vm::setMotion)
            Text(
                when (setup.motion) {
                    MotionMode.MANUAL -> "La app te pide mover la lámina al campo siguiente y esperas a pulsar Continuar."
                    MotionMode.SIMULATED -> "El movimiento se simula con retardos, para ensayar sin el equipo motorizado."
                    MotionMode.BLE_SIM -> "Corre el protocolo BLE completo (ACK, reintentos, vigilancia por PING) contra un controlador ESP32 simulado."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Motorizado por BLE real: requiere el controlador del equipo y el acuerdo de UUID con el firmware, aún pendientes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Legend("Imágenes")
            Segmented(listOf(ImageSource.CAMERA to "Cámara", ImageSource.SAMPLES to "Muestras"), setup.source, vm::setSource)
            Text(
                if (setup.source == ImageSource.CAMERA) "Enfoca tocando la vista previa. Al iniciar se bloquean exposición y balance de blancos." else "Recorre las muestras del conjunto público como campos de la lámina: plan B de la demostración.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (setup.source == ImageSource.CAMERA) {
                PlateFrame(Modifier.fillMaxWidth().height(260.dp)) {
                    if (hasCamera) CameraPreviewView(vm.camera) else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("La cámara necesita permiso", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        MicaButton({ ask.launch(Manifest.permission.CAMERA) }) { Text("Permitir cámara", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }

        MicaButton(vm::start, Modifier.fillMaxWidth(), enabled = setup.source != ImageSource.CAMERA || hasCamera) {
            Text("Iniciar sesión", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActivePanel(vm: SessionViewModel, run: RunUi, onOpenResults: (String) -> Unit) {
    val s = run.scan
    var confirmCancel by remember { mutableStateOf(false) }
    val finished = s.phase.terminal
    val done = run.rows.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Legend(run.setup.label.ifBlank { "Sesión sin identificador" })
            Text(
                when (s.phase) {
                    ScanPhase.COMPLETED -> "Sesión completa"
                    ScanPhase.CANCELLED -> "Sesión cancelada"
                    ScanPhase.FAILED -> "Sesión detenida"
                    else -> "Campo ${s.index + 1} de ${s.total}"
                },
                style = MaterialTheme.typography.headlineSmall.tabular,
            )
            LinearProgressIndicator(
                progress = { if (s.total == 0) 0f else done.toFloat() / s.total },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$done de ${s.total} campos procesados" },
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(phaseText(s), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (!finished && run.setup.source == ImageSource.CAMERA) PlateFrame(Modifier.fillMaxWidth().height(220.dp)) { CameraPreviewView(vm.camera) }

        run.manualPrompt?.let {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mueve la lámina al campo ${s.index + 1}", style = MaterialTheme.typography.titleMedium)
                Text("Cuando la imagen esté quieta, pulsa Continuar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                MicaButton(vm::confirmMove, Modifier.fillMaxWidth()) { Text("Continuar", style = MaterialTheme.typography.labelLarge) }
            }
        }

        if (s.phase == ScanPhase.PAUSED && s.message != null && s.message != "En pausa") {
            Message(s.message, onDismiss = null)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Reading("Válidos", s.validFields.toString(), Modifier.weight(1f))
            Reading("Sin calidad", s.invalidFields.toString(), Modifier.weight(1f), if (s.invalidFields > 0) Mica.stain.warning else MaterialTheme.colorScheme.onSurface)
            Reading("Fallidos", s.failedFields.toString(), Modifier.weight(1f), if (s.failedFields > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }

        if (finished) {
            if (s.phase == ScanPhase.FAILED && s.message != null) Message(s.message, onDismiss = null)
            MicaButton({ onOpenResults(run.sessionId) }, Modifier.fillMaxWidth()) { Text("Ver resultados", style = MaterialTheme.typography.labelLarge) }
            MicaOutlinedButton(vm::close, Modifier.fillMaxWidth()) { Text("Cerrar", style = MaterialTheme.typography.labelLarge) }
        } else {
            Controls(vm, s) { confirmCancel = true }
        }

        if (run.rows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Legend("Campos procesados", Modifier.padding(bottom = 8.dp))
                run.rows.asReversed().forEach { FieldLine(it) }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar la sesión?") },
            text = { Text("Se detiene el movimiento. Los campos ya guardados se conservan y la sesión queda como cancelada.") },
            confirmButton = { TextButton({ confirmCancel = false; vm.cancel() }, Modifier.height(48.dp)) { Text("Cancelar sesión", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmCancel = false }, Modifier.height(48.dp)) { Text("Seguir") } },
        )
    }
}

@Composable
private fun Controls(vm: SessionViewModel, s: ScanState, onCancel: () -> Unit) {
    val paused = s.phase == ScanPhase.PAUSED
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (paused) {
            MicaButton(vm::resume, Modifier.fillMaxWidth()) { MicaIcon(MicaIcons.Play, null, tint = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.size(8.dp)); Text(if (s.canSkip) "Reintentar" else "Reanudar", style = MaterialTheme.typography.labelLarge) }
            if (s.canSkip) MicaOutlinedButton(vm::skip, Modifier.fillMaxWidth()) { Text("Omitir este campo", style = MaterialTheme.typography.labelLarge) }
        } else {
            MicaOutlinedButton(vm::pause, Modifier.fillMaxWidth(), enabled = s.phase != ScanPhase.CANCELLING) { MicaIcon(MicaIcons.Pause, null); Spacer(Modifier.size(8.dp)); Text("Pausar al terminar el campo", style = MaterialTheme.typography.labelLarge) }
        }
        MicaOutlinedButton(onCancel, Modifier.fillMaxWidth(), enabled = s.phase != ScanPhase.CANCELLING) { Text("Cancelar sesión", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FieldLine(row: FieldRow) {
    Column {
        HairlineDivider()
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ok = row.status == FieldStatus.VALID
            MicaIcon(if (ok) MicaIcons.Check else MicaIcons.Warning, null, tint = if (ok) Mica.stain.ok else Mica.stain.warning, size = 20.dp)
            Text("Campo ${row.idx + 1}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.tabular)
            Text(
                when (row.status) {
                    FieldStatus.VALID -> pe.utec.micatb.ui.results.counted(row.countStrict ?: 0)
                    FieldStatus.INVALID_QUALITY -> "Sin calidad"
                    FieldStatus.FAILED_MOTION -> "Movimiento fallido"
                    FieldStatus.FAILED_OTHER -> "Captura fallida"
                    else -> "Omitido"
                },
                style = MaterialTheme.typography.bodyMedium.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun phaseText(s: ScanState): String = when (s.phase) {
    ScanPhase.IDLE, ScanPhase.PREPARING -> "Preparando"
    ScanPhase.MOVING -> "Moviendo la lámina"
    ScanPhase.SETTLING -> "Esperando a que la imagen se estabilice"
    ScanPhase.CAPTURING -> if (s.attempt > 1) "Capturando, intento ${s.attempt}" else "Capturando"
    ScanPhase.QUALITY_CHECK -> "Comprobando la calidad"
    ScanPhase.INFERRING -> "Contando bacilos"
    ScanPhase.STORING -> "Guardando el campo"
    ScanPhase.RETRYING -> "Calidad insuficiente, volviendo a capturar"
    ScanPhase.PAUSED -> if (s.message == "En pausa") "En pausa. El siguiente movimiento no empieza hasta reanudar." else "En pausa. Elige cómo continuar."
    ScanPhase.ERROR -> "Error: ${s.message}"
    ScanPhase.CANCELLING -> "Cancelando"
    ScanPhase.CANCELLED -> "El movimiento se detuvo. Los campos guardados se conservan."
    ScanPhase.COMPLETED -> "Todos los campos procesados."
    ScanPhase.FAILED -> "Se detuvo por un error del movimiento."
}

@Composable
private fun Message(text: String, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicaIcon(MicaIcons.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(text, Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        if (onDismiss != null) TextButton(onDismiss, Modifier.height(48.dp)) { Text("Cerrar", color = MaterialTheme.colorScheme.onErrorContainer) }
    }
}
