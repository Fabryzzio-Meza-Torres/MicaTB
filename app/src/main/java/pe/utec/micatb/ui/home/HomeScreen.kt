package pe.utec.micatb.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.utec.micatb.AppContainer
import pe.utec.micatb.ui.components.HairlineDivider
import pe.utec.micatb.ui.components.MicaButton
import pe.utec.micatb.ui.components.MicaOutlinedButton
import pe.utec.micatb.ui.components.Legend
import pe.utec.micatb.ui.components.Reading
import pe.utec.micatb.ui.theme.tabular

@Composable
fun HomeScreen(container: AppContainer, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val cfg = container.modelConfig
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mica TB", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Captura campos del microscopio, cuenta bacilos sin conexión y deja cada número sobre su foto original.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MicaButton(onClick = { onNavigate("session") }, Modifier.fillMaxWidth().height(56.dp)) { Text("Nueva sesión", style = MaterialTheme.typography.labelLarge) }
            MicaOutlinedButton(onClick = { onNavigate("capture") }, Modifier.fillMaxWidth().height(56.dp)) { Text("Capturar un campo suelto", style = MaterialTheme.typography.labelLarge) }
            MicaOutlinedButton(onClick = { onNavigate("results") }, Modifier.fillMaxWidth().height(56.dp)) { Text("Historial de sesiones", style = MaterialTheme.typography.labelLarge) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HairlineDivider()
            Legend("Modelo activo")
            Text(cfg.modelId, style = MaterialTheme.typography.titleMedium.tabular)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Reading("Variante", container.variantName.uppercase(), Modifier.weight(1f))
                Reading("Entrada", "${cfg.input.width}×${cfg.input.height}", Modifier.weight(1f))
                Reading("Clases", cfg.classes.size.toString(), Modifier.weight(1f))
            }
            Text(
                "Huella ${container.activeSha256.take(8)}…${container.activeSha256.takeLast(6)}",
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
