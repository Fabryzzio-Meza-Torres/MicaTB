package pe.utec.micatb.data

import org.json.JSONArray
import org.json.JSONObject
import pe.utec.micatb.domain.ModelConfig
import pe.utec.micatb.inference.RawDetections
import pe.utec.micatb.inference.countFor
import pe.utec.micatb.quality.QualityReport

/** Resultado de un campo tal como se guarda junto a la foto original (RF18). Sin cajas dibujadas sobre la foto. */
object ResultJson {
    const val VERSION = 1

    fun build(
        config: ModelConfig,
        variant: String,
        sha256: String,
        raw: RawDetections,
        quality: QualityReport,
        strictConf: Float,
        broadConf: Float,
        capturedAtMs: Long,
        source: String,
    ): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("source", source)
        put("captured_at", capturedAtMs)
        put("model", JSONObject().put("id", config.modelId).put("variant", variant).put("sha256", sha256))
        put("image", JSONObject().put("width", raw.imageWidth).put("height", raw.imageHeight))
        put("base_conf", config.post.baseConf)
        put("nms_iou", config.post.nmsIou)
        put(
            "counts",
            JSONObject()
                .put("strict", JSONObject().put("conf", strictConf.toDouble()).put("count", raw.detections.countFor(config.strict, config.classes, strictConf)))
                .put("broad", JSONObject().put("conf", broadConf.toDouble()).put("count", raw.detections.countFor(config.broad, config.classes, broadConf))),
        )
        put(
            "quality",
            JSONObject()
                .put("sharpness", quality.sharpness.toDouble())
                .put("brightness", quality.brightness.toDouble())
                .put("clipped_fraction", quality.clippedFraction.toDouble())
                .put("flags", JSONArray(quality.flags.map { it.name })),
        )
        put(
            "timings_ms",
            JSONObject().put("pre", raw.timings.preMs).put("infer", raw.timings.inferMs).put("post", raw.timings.postMs),
        )
        // Detecciones crudas tras NMS a base_conf: permiten volver a filtrar sin reejecutar el modelo.
        put(
            "detections",
            JSONArray(
                raw.detections.map {
                    JSONObject().put("cls", it.cls).put("class", config.classes[it.cls]).put("score", it.score.toDouble())
                        .put("box", JSONArray(listOf(it.x1, it.y1, it.x2, it.y2).map { v -> v.toDouble() }))
                },
            ),
        )
    }
}
