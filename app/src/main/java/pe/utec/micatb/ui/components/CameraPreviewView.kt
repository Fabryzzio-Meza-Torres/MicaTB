package pe.utec.micatb.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import pe.utec.micatb.camera.CameraSource

/** Vista previa con "tocar para enfocar": un anillo marca el punto y el enfoque se mantiene hasta desbloquear. */
@Composable
fun CameraPreviewView(camera: CameraSource, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<PreviewView?>(null) }
    var ring by remember { mutableStateOf<Offset?>(null) }
    val ringColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(view) { view?.let { camera.bind(owner, it) } }
    DisposableEffect(Unit) { onDispose { camera.unbind() } }
    Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { p -> ring = p; camera.focusAt(p.x, p.y) } }) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also { view = it }
            },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Vista previa de la cámara. Toca para enfocar." },
        )
        ring?.let { p -> Canvas(Modifier.fillMaxSize()) { drawCircle(ringColor, radius = 28.dp.toPx(), center = p, style = Stroke(2.dp.toPx())) } }
    }
}
