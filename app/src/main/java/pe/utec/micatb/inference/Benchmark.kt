package pe.utec.micatb.inference

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import pe.utec.micatb.domain.ModelConfig

/** Mediana y percentil 95 de una etapa, en milisegundos. */
data class StageStats(val medianMs: Double, val p95Ms: Double)

data class BenchmarkResult(
    val variant: String,
    val threads: Int,
    val runs: Int,
    val pre: StageStats,
    val infer: StageStats,
    val post: StageStats,
    /** Conteo estricto de la imagen de prueba: debe ser igual entre variantes y delegados. */
    val countStrict: Int,
)

object Stats {
    /** Percentil por rango mas cercano sobre datos ordenados internamente. */
    fun percentile(values: List<Double>, p: Double): Double {
        require(values.isNotEmpty()) { "sin datos" }
        val s = values.sorted()
        val rank = Math.ceil(p / 100.0 * s.size).toInt().coerceIn(1, s.size)
        return s[rank - 1]
    }

    fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    fun stage(values: List<Double>) = StageStats(median(values), percentile(values, 95.0))
}

/** Protocolo de docs/MODEL_CONTRACT.md seccion 7: 5 inferencias de calentamiento y 50 medidas sobre la misma imagen. */
object Benchmark {
    const val WARMUP = 5
    const val RUNS = 50

    fun run(context: Context, config: ModelConfig, variant: String?, threads: Int, image: Bitmap, onProgress: (Int) -> Unit = {}): BenchmarkResult {
        LiteRtDetector(context, config, variant, threads).use { d ->
            repeat(WARMUP) { d.detect(image) }
            val pre = ArrayList<Double>(RUNS)
            val infer = ArrayList<Double>(RUNS)
            val post = ArrayList<Double>(RUNS)
            var count = 0
            repeat(RUNS) { i ->
                val r = d.detect(image)
                pre += r.timings.preMs; infer += r.timings.inferMs; post += r.timings.postMs
                count = r.detections.countFor(config.strict, config.classes)
                onProgress(i + 1)
            }
            return BenchmarkResult(variant ?: "fp16", threads, RUNS, Stats.stage(pre), Stats.stage(infer), Stats.stage(post), count)
        }
    }

    /** Informe en JSON con el celular y la version de Android, para anotarlo en PROGRESS.md. */
    fun toJson(results: List<BenchmarkResult>, imageName: String, config: ModelConfig, createdAtMs: Long): JSONObject = JSONObject().apply {
        put("created_at", createdAtMs)
        put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT).put("abi", Build.SUPPORTED_ABIS.firstOrNull()))
        put("model_id", config.modelId)
        put("image", imageName)
        put("warmup", WARMUP)
        put(
            "results",
            JSONArray(
                results.map {
                    fun s(x: StageStats) = JSONObject().put("median_ms", x.medianMs).put("p95_ms", x.p95Ms)
                    JSONObject().put("variant", it.variant).put("threads", it.threads).put("runs", it.runs)
                        .put("pre", s(it.pre)).put("infer", s(it.infer)).put("post", s(it.post)).put("count_strict", it.countStrict)
                },
            ),
        )
    }
}
