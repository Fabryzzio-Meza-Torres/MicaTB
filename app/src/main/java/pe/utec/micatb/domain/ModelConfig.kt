package pe.utec.micatb.domain

import org.json.JSONObject

data class CountingRule(val classes: List<String>, val conf: Float)

data class ModelConfig(
    val modelId: String,
    val file: String,
    val sha256: String,
    val domainNote: String,
    val classes: List<String>,
    val strict: CountingRule,
    val broad: CountingRule,
) {
    companion object {
        fun parse(json: String): ModelConfig {
            val o = JSONObject(json)
            val counting = o.getJSONObject("counting")
            return ModelConfig(
                modelId = o.getString("model_id"),
                file = o.getString("file"),
                sha256 = o.getString("sha256"),
                domainNote = o.getString("domain_note"),
                classes = o.getJSONArray("classes").strings(),
                strict = counting.getJSONObject("strict").rule(),
                broad = counting.getJSONObject("broad").rule(),
            )
        }

        private fun org.json.JSONArray.strings() = List(length()) { getString(it) }

        private fun JSONObject.rule() =
            CountingRule(getJSONArray("classes").strings(), getDouble("conf").toFloat())
    }
}