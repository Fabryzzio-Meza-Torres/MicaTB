package pe.utec.micatb.domain

import org.json.JSONObject

data class QualityConfig(
    val analysisSize: Int,
    val sharpnessMin: Float,
    val brightnessMin: Float,
    val brightnessMax: Float,
    val clippedMax: Float,
    /** false mientras los umbrales sean los iniciales y no se hayan calibrado con el microscopio. */
    val calibrated: Boolean,
)

data class SessionConfig(
    val defaultFields: Int,
    val settleMs: Long,
    val maxRetries: Int,
    /** "pause" u "skip". */
    val onRetriesExhausted: String,
    val minValidFieldsForGrade: Int,
)

data class GradeBand(val maxMeanPerField: Double, val grade: String, val text: String)

data class GradingConfig(val label: String, val bands: List<GradeBand>)

/** Constantes de dominio de la app, leidas de assets/app_config.json. */
data class AppConfig(
    val quality: QualityConfig,
    val session: SessionConfig,
    val jpegQuality: Int,
    val grading: GradingConfig,
) {
    companion object {
        fun parse(json: String): AppConfig {
            val o = JSONObject(json)
            val q = o.getJSONObject("quality")
            val s = o.getJSONObject("session")
            val g = o.getJSONObject("grading")
            val bands = g.getJSONArray("bands")
            return AppConfig(
                quality = QualityConfig(
                    analysisSize = q.getInt("analysis_size"),
                    sharpnessMin = q.getDouble("sharpness_min").toFloat(),
                    brightnessMin = q.getDouble("brightness_min").toFloat(),
                    brightnessMax = q.getDouble("brightness_max").toFloat(),
                    clippedMax = q.getDouble("clipped_max").toFloat(),
                    calibrated = q.getBoolean("calibrated"),
                ),
                session = SessionConfig(
                    defaultFields = s.getInt("default_fields"),
                    settleMs = s.getLong("settle_ms"),
                    maxRetries = s.getInt("max_retries"),
                    onRetriesExhausted = s.getString("on_retries_exhausted"),
                    minValidFieldsForGrade = s.getInt("min_valid_fields_for_grade"),
                ),
                jpegQuality = o.getJSONObject("capture").getInt("jpeg_quality"),
                grading = GradingConfig(
                    label = g.getString("label"),
                    bands = List(bands.length()) {
                        val b = bands.getJSONObject(it)
                        GradeBand(b.getDouble("max_mean_per_field"), b.getString("grade"), b.getString("text"))
                    },
                ),
            )
        }
    }
}
