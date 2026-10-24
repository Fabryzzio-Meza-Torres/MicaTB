package pe.utec.micatb.domain

import org.json.JSONArray
import org.json.JSONObject

/** Regla de conteo: clases que cuentan y puntaje minimo (comparacion estricta `score > conf`). */
data class CountingRule(val classes: List<String>, val conf: Float)

data class InputSpec(
    val width: Int,
    val height: Int,
    /** Lado del letterbox antes de anadir el borde. */
    val content: Int,
    /** Pixeles de relleno por lado alrededor del letterbox. */
    val border: Int,
    val padValue: Int,
    val scale: Float,
)

data class OutputSpec(val channels: Int, val anchors: Int)

data class PostSpec(val baseConf: Float, val nmsIou: Float, val maxDet: Int)

data class ModelVariant(val file: String, val sha256: String)

/** Contrato del modelo leido de models/model_config.json. Nada del modelo vive en el codigo. */
data class ModelConfig(
    val modelId: String,
    val file: String,
    val sha256: String,
    val variants: Map<String, ModelVariant>,
    val domainNote: String,
    val input: InputSpec,
    val output: OutputSpec,
    val classes: List<String>,
    val post: PostSpec,
    val strict: CountingRule,
    val broad: CountingRule,
) {
    companion object {
        fun parse(json: String): ModelConfig {
            val o = JSONObject(json)
            val inp = o.getJSONObject("input")
            val shape = o.getJSONObject("output").getJSONArray("shape")
            val post = o.getJSONObject("postprocess")
            val counting = o.getJSONObject("counting")
            val variants = o.optJSONObject("variants")
            return ModelConfig(
                modelId = o.getString("model_id"),
                file = o.getString("file"),
                sha256 = o.getString("sha256"),
                variants = variants?.keys()?.asSequence()?.associateWith {
                    ModelVariant(variants.getJSONObject(it).getString("file"), variants.getJSONObject(it).getString("sha256"))
                } ?: emptyMap(),
                domainNote = o.getString("domain_note"),
                input = InputSpec(
                    width = inp.getInt("width"),
                    height = inp.getInt("height"),
                    content = inp.getInt("content"),
                    border = inp.getInt("border"),
                    padValue = inp.getInt("pad_value"),
                    scale = inp.getDouble("scale").toFloat(),
                ),
                output = OutputSpec(channels = shape.getInt(1), anchors = shape.getInt(2)),
                classes = o.getJSONArray("classes").strings(),
                post = PostSpec(
                    baseConf = post.getDouble("base_conf").toFloat(),
                    nmsIou = post.getDouble("nms_iou").toFloat(),
                    maxDet = post.getInt("max_det"),
                ),
                strict = counting.getJSONObject("strict").rule(),
                broad = counting.getJSONObject("broad").rule(),
            )
        }

        private fun JSONArray.strings() = List(length()) { getString(it) }

        private fun JSONObject.rule() = CountingRule(getJSONArray("classes").strings(), getDouble("conf").toFloat())
    }
}
