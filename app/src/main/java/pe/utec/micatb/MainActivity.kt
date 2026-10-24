package pe.utec.micatb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.utec.micatb.ui.MicaApp
import pe.utec.micatb.ui.theme.MicaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MicaApplication).container
        setContent { MicaTheme { MicaApp(container) } }
    }
}