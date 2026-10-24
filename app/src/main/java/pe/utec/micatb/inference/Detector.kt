package pe.utec.micatb.inference

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.tensorflow.lite.Interpreter
import pe.utec.micatb.domain.ModelConfig

/** Filas de un [Bitmap] como ARGB, sin copiar la foto entera. */
class BitmapRows(private val bitmap: Bitmap) : PixelRows {
    override val width get() = bitmap.width
    override val height get() = bitmap.height
    override fun readRow(y: Int, out: IntArray) = bitmap.getPixels(out, 0, bitmap.width, 0, y, bitmap.width, 1)
}

/**
 * Detector LiteRT en CPU. Carga el .tflite de assets segun [ModelConfig] (nada del modelo esta en el codigo)
 * y verifica su sha256 contra la configuracion antes de usarlo.
 */
class LiteRtDetector(
    context: Context,
    val config: ModelConfig,
    variant: String? = null,
    threads: Int = 4,
) : AutoCloseable {
    val modelFile: String
    private val interpreter: Interpreter
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer
    private val tensor: FloatArray
    private val raw: FloatArray

    init {
        val (file, sha) = variant?.let { v ->
            val m = requireNotNull(config.variants[v]) { "variante desconocida: $v" }
            m.file to m.sha256
        } ?: (config.file to config.sha256)
        modelFile = file
        val bytes = context.assets.open(file).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == sha) { "sha256 de $file no coincide con model_config.json" }

        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(threads))
        val side = config.input.width
        tensor = FloatArray(3 * side * side)
        raw = FloatArray(config.output.channels * config.output.anchors)
        inputBuffer = ByteBuffer.allocateDirect(tensor.size * 4).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(raw.size * 4).order(ByteOrder.nativeOrder())
    }

    /** Ejecuta el modelo sobre la imagen y devuelve las detecciones tras NMS a base_conf. */
    @Synchronized
    fun detect(bitmap: Bitmap): RawDetections {
        val t0 = System.nanoTime()
        val lb = Preprocess.toTensor(BitmapRows(bitmap), config.input, tensor)
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(tensor)
        val t1 = System.nanoTime()

        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        val t2 = System.nanoTime()

        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(raw)
        val detections = Postprocess.run(raw, config, lb, bitmap.width, bitmap.height)
        val t3 = System.nanoTime()

        return RawDetections(
            detections, bitmap.width, bitmap.height,
            Timings(preMs = (t1 - t0) / 1e6, inferMs = (t2 - t1) / 1e6, postMs = (t3 - t2) / 1e6),
        )
    }

    override fun close() = interpreter.close()
}
