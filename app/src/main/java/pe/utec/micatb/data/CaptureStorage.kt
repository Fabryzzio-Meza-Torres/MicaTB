package pe.utec.micatb.data

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Almacenamiento privado de capturas: la foto original sin cajas y un JSON con el resultado.
 * Se escribe primero un archivo temporal y despues se renombra, para no dejar archivos a medias.
 */
class CaptureStorage(context: Context) {
    val root: File = File(context.filesDir, "captures").apply { mkdirs() }

    fun dir(group: String): File = File(root, group).apply { mkdirs() }

    /** Guarda la foto original ya existente (la del disparador) y su resultado. Devuelve el JSON final. */
    fun commit(group: String, id: String, originalJpeg: File, result: JSONObject): File {
        val dir = dir(group)
        val jpg = File(dir, "$id.jpg")
        if (originalJpeg.canonicalFile != jpg.canonicalFile) originalJpeg.copyTo(jpg, overwrite = true)
        return writeJson(dir, id, result)
    }

    /** Guarda bytes originales (por ejemplo una muestra) y su resultado. */
    fun commit(group: String, id: String, originalBytes: ByteArray, result: JSONObject): File {
        val dir = dir(group)
        val tmp = File(dir, "$id.jpg.tmp").apply { writeBytes(originalBytes) }
        check(tmp.renameTo(File(dir, "$id.jpg"))) { "no se pudo guardar la foto $id" }
        return writeJson(dir, id, result)
    }

    private fun writeJson(dir: File, id: String, result: JSONObject): File {
        val tmp = File(dir, "$id.json.tmp").apply { writeText(result.toString(1)) }
        val json = File(dir, "$id.json")
        check(tmp.renameTo(json)) { "no se pudo guardar el resultado $id" }
        return json
    }

    fun delete(group: String, id: String) {
        File(dir(group), "$id.jpg").delete()
        File(dir(group), "$id.json").delete()
    }
}
