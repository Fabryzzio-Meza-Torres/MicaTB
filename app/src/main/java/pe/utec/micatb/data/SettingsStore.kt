package pe.utec.micatb.data

import android.content.Context
import androidx.core.content.edit
import pe.utec.micatb.domain.QualityConfig

enum class CountMode { STRICT, BROAD }

/** Ajustes del operador que sobreviven al cierre de la app. Los valores por defecto salen de la configuracion. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("mica_settings", Context.MODE_PRIVATE)

    var countMode: CountMode
        get() = CountMode.valueOf(prefs.getString("count_mode", CountMode.STRICT.name)!!)
        set(v) = prefs.edit { putString("count_mode", v.name) }

    /** Umbral elegido en el deslizador; null = el de la regla de conteo. */
    var confOverride: Float?
        get() = if (prefs.contains("conf")) prefs.getFloat("conf", 0f) else null
        set(v) = prefs.edit { if (v == null) remove("conf") else putFloat("conf", v) }

    /** Variante del modelo: null = la de model_config.json (FP16); "int8" para medir. */
    var modelVariant: String?
        get() = prefs.getString("variant", null)
        set(v) = prefs.edit { if (v == null) remove("variant") else putString("variant", v) }

    var threads: Int
        get() = prefs.getInt("threads", 4)
        set(v) = prefs.edit { putInt("threads", v) }

    /** Umbral de nitidez calibrado con el microscopio; null = el inicial de app_config.json. */
    var sharpnessMin: Float?
        get() = if (prefs.contains("sharp_min")) prefs.getFloat("sharp_min", 0f) else null
        set(v) = prefs.edit { if (v == null) remove("sharp_min") else putFloat("sharp_min", v) }

    /** Ancho real del campo en micrometros; null = sin calibrar y no se dibuja barra de escala. */
    var fieldWidthUm: Float?
        get() = if (prefs.contains("field_um")) prefs.getFloat("field_um", 0f) else null
        set(v) = prefs.edit { if (v == null) remove("field_um") else putFloat("field_um", v) }

    var lastSampleLabel: String
        get() = prefs.getString("sample_label", "")!!
        set(v) = prefs.edit { putString("sample_label", v) }

    fun effectiveQuality(base: QualityConfig): QualityConfig =
        sharpnessMin?.let { base.copy(sharpnessMin = it, calibrated = true) } ?: base
}
