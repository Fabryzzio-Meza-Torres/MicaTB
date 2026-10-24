package pe.utec.micatb.data

import org.json.JSONArray
import org.json.JSONObject
import pe.utec.micatb.inference.Detection

/** Detecciones crudas como JSON compacto para guardarlas en la base y volver a filtrarlas sin reejecutar el modelo. */
object DetectionsJson {
    fun encode(dets: List<Detection>): String = JSONArray(
        dets.map {
            JSONObject().put("c", it.cls).put("s", it.score.toDouble()).put("b", JSONArray(listOf(it.x1, it.y1, it.x2, it.y2).map { v -> v.toDouble() }))
        },
    ).toString()

    fun decode(json: String): List<Detection> {
        val a = JSONArray(json)
        return List(a.length()) {
            val o = a.getJSONObject(it)
            val b = o.getJSONArray("b")
            Detection(o.getInt("c"), o.getDouble("s").toFloat(), b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat())
        }
    }
}
