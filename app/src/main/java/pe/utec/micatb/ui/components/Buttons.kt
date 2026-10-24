package pe.utec.micatb.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Boton relleno con la forma del sistema (esquina de 4 dp) y area tactil de 56 dp para uso con guantes. */
@Composable
fun MicaButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = 56.dp), enabled = enabled, shape = MaterialTheme.shapes.small, content = content)
}

@Composable
fun MicaOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 56.dp), enabled = enabled, shape = MaterialTheme.shapes.small, content = content)
}
