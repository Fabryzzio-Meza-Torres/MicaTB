package pe.utec.micatb

import android.app.Application

class MicaApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
