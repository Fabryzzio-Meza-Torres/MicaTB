package pe.utec.micatb.data

import androidx.room.withTransaction
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import pe.utec.micatb.data.db.CaptureEntity
import pe.utec.micatb.data.db.FieldEntity
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.data.db.InferenceEntity
import pe.utec.micatb.data.db.MicaDatabase
import pe.utec.micatb.data.db.SessionEntity
import pe.utec.micatb.data.db.SessionStatus

/** Posicion de un campo en la lamina, en pasos del motor (o 0, 0 si el movimiento es manual). */
data class FieldPos(val idx: Int, val x: Int, val y: Int)

sealed interface PhotoSource {
    class OnDisk(val file: File) : PhotoSource
    class Bytes(val bytes: ByteArray) : PhotoSource
}

class PhotoInput(val source: PhotoSource, val width: Int, val height: Int, val attempt: Int)

class InferenceInput(
    val modelId: String, val baseConf: Float, val conf: Float, val nmsIou: Float,
    val countStrict: Int, val countBroad: Int, val detectionsJson: String,
    val tPreMs: Double, val tInferMs: Double, val tPostMs: Double,
)

class FieldOutcome(
    val status: FieldStatus,
    val attempts: Int,
    val qualityJson: String?,
    val photo: PhotoInput? = null,
    val inference: InferenceInput? = null,
)

data class SessionSummary(
    val id: String, val createdAt: Long, val finishedAt: Long?, val status: SessionStatus,
    val label: String?, val modelId: String, val fields: Int, val validFields: Int, val countStrict: Int, val countBroad: Int,
)

data class FieldDetail(val field: FieldEntity, val capture: CaptureEntity?, val inference: InferenceEntity?)

data class SessionDetail(val session: SessionEntity, val fields: List<FieldDetail>)

/**
 * Sesiones, campos, capturas e inferencias. Integridad (SPEC seccion 7): la foto se escribe primero en un
 * temporal y se renombra; despues la fila se guarda en una transaccion. Si la transaccion falla, se borra la foto.
 */
class SessionRepository(
    private val db: MicaDatabase,
    private val photosDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Solo para pruebas: se llama dentro de la transaccion para simular un fallo tras escribir la foto. */
    internal var beforeCommit: (() -> Unit)? = null

    suspend fun createSession(label: String?, modelId: String, modelSha256: String, configJson: String): String {
        val id = UUID.randomUUID().toString()
        db.sessions().insert(SessionEntity(id, clock(), null, SessionStatus.CREATED.name, label?.trim()?.ifEmpty { null }, modelId, modelSha256, configJson, null))
        return id
    }

    /** Crea las filas de campo que falten, en estado PENDING. Idempotente. */
    suspend fun prepareFields(sessionId: String, positions: List<FieldPos>) = db.withTransaction {
        for (p in positions) {
            if (db.fields().get(sessionId, p.idx) == null) {
                db.fields().insert(FieldEntity(sessionId = sessionId, idx = p.idx, x = p.x, y = p.y, status = FieldStatus.PENDING.name, attempts = 0, qualityJson = null))
            }
        }
    }

    suspend fun setStatus(sessionId: String, status: SessionStatus) {
        val finished = if (status == SessionStatus.COMPLETED || status == SessionStatus.CANCELLED || status == SessionStatus.FAILED) clock() else null
        db.sessions().setStatus(sessionId, status.name, finished)
    }

    suspend fun setNotes(sessionId: String, notes: String?) = db.sessions().setNotes(sessionId, notes?.trim()?.ifEmpty { null })

    /** Guarda el resultado de un campo: foto primero, fila despues. Una sola transaccion. */
    suspend fun record(sessionId: String, idx: Int, outcome: FieldOutcome) {
        var written: File? = null
        var rel: String? = null
        var sha = ""
        outcome.photo?.let { p ->
            val dir = File(photosDir, sessionId).apply { mkdirs() }
            val name = "$idx-${p.attempt}.jpg"
            val target = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            withContext(Dispatchers.IO) {
                when (val s = p.source) {
                    is PhotoSource.OnDisk -> s.file.copyTo(tmp, overwrite = true)
                    is PhotoSource.Bytes -> tmp.writeBytes(s.bytes)
                }
                sha = sha256(tmp)
                check(tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) { "no se pudo guardar la foto del campo $idx" }
            }
            written = target
            rel = "$sessionId/$name"
        }
        try {
            db.withTransaction {
                val field = checkNotNull(db.fields().get(sessionId, idx)) { "campo $idx sin preparar" }
                db.fields().update(field.copy(status = outcome.status.name, attempts = outcome.attempts, qualityJson = outcome.qualityJson))
                val photo = outcome.photo
                if (photo != null) {
                    val captureId = db.captures().insert(CaptureEntity(fieldId = field.id, path = rel!!, width = photo.width, height = photo.height, sha256 = sha, capturedAt = clock(), attempt = photo.attempt))
                    outcome.inference?.let { i ->
                        db.inferences().insert(
                            InferenceEntity(
                                captureId = captureId, modelId = i.modelId, baseConf = i.baseConf, conf = i.conf, nmsIou = i.nmsIou,
                                countStrict = i.countStrict, countBroad = i.countBroad, detectionsJson = i.detectionsJson,
                                tPreMs = i.tPreMs, tInferMs = i.tInferMs, tPostMs = i.tPostMs, createdAt = clock(),
                            ),
                        )
                    }
                }
                beforeCommit?.invoke()
            }
        } catch (e: Throwable) {
            written?.delete()
            throw e
        }
    }

    /** Sesiones aun abiertas (RUNNING o PAUSED), por ejemplo tras cerrar la app a mitad de un barrido. */
    suspend fun unfinished(): List<SessionEntity> = db.sessions().unfinished()

    suspend fun session(id: String): SessionEntity? = db.sessions().get(id)

    /** Posiciones guardadas de los campos de la sesion, para reanudarla. */
    suspend fun positions(sessionId: String): List<FieldPos> = db.fields().list(sessionId).map { FieldPos(it.idx, it.x, it.y) }

    /** Primer indice de campo que aun no tiene resultado final: desde ahi se reanuda. */
    suspend fun resumeIndex(sessionId: String): Int =
        db.fields().list(sessionId).firstOrNull { it.status == FieldStatus.PENDING.name || it.status == FieldStatus.FAILED_MOTION.name || it.status == FieldStatus.FAILED_OTHER.name }?.idx
            ?: db.fields().list(sessionId).size

    fun observeSummaries(): Flow<List<SessionSummary>> =
        combine(db.sessions().observeAll(), db.fields().observeStats(), db.inferences().observeCounts()) { sessions, stats, counts ->
            val st = stats.associateBy { it.sessionId }
            val ct = counts.associateBy { it.sessionId }
            sessions.map {
                SessionSummary(
                    it.id, it.createdAt, it.finishedAt, SessionStatus.valueOf(it.status), it.sampleLabel, it.modelId,
                    st[it.id]?.fields ?: 0, st[it.id]?.valid ?: 0, ct[it.id]?.strict ?: 0, ct[it.id]?.broad ?: 0,
                )
            }
        }

    suspend fun detail(sessionId: String): SessionDetail? {
        val session = db.sessions().get(sessionId) ?: return null
        val captures = db.captures().listForSession(sessionId)
        val inferences = db.inferences().listForSession(sessionId).associateBy { it.captureId }
        val lastByField = captures.groupBy { it.fieldId }.mapValues { (_, v) -> v.maxBy { it.attempt } }
        val fields = db.fields().list(sessionId).map { f ->
            val c = lastByField[f.id]
            FieldDetail(f, c, c?.let { inferences[it.id] })
        }
        return SessionDetail(session, fields)
    }

    fun photoFile(capture: CaptureEntity): File = File(photosDir, capture.path)

    suspend fun delete(sessionId: String) {
        db.sessions().delete(sessionId)
        withContext(Dispatchers.IO) { File(photosDir, sessionId).deleteRecursively() }
    }

    /**
     * Borra fotos sin fila (cortes a mitad de guardado), temporales y carpetas de sesiones que ya no existen.
     * Devuelve cuantos archivos borro.
     */
    suspend fun reconcileOrphans(): Int = withContext(Dispatchers.IO) {
        var removed = 0
        val dirs = photosDir.listFiles { f -> f.isDirectory }.orEmpty()
        for (dir in dirs) {
            if (db.sessions().get(dir.name) == null) {
                removed += dir.walkTopDown().count { it.isFile }
                dir.deleteRecursively()
                continue
            }
            val known = db.captures().pathsForSession(dir.name).map { File(photosDir, it).canonicalPath }.toSet()
            for (f in dir.listFiles().orEmpty()) {
                if (f.isFile && (f.name.endsWith(".tmp") || f.canonicalPath !in known)) { f.delete(); removed++ }
            }
        }
        removed
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
