package pe.utec.micatb.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SessionStatus { CREATED, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED }

enum class FieldStatus { PENDING, VALID, INVALID_QUALITY, FAILED_MOTION, FAILED_OTHER, SKIPPED }

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    val status: String,
    /** Texto libre y local. Nunca datos de pacientes. */
    @ColumnInfo(name = "sample_label") val sampleLabel: String?,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "model_sha256") val modelSha256: String,
    /** Barrido, umbrales, calidad y modo de movimiento con que se corrio la sesion. */
    @ColumnInfo(name = "config_json") val configJson: String,
    val notes: String?,
)

@Entity(
    tableName = "fields",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["session_id", "idx"], unique = true)],
)
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val idx: Int,
    val x: Int,
    val y: Int,
    val status: String,
    val attempts: Int,
    @ColumnInfo(name = "quality_json") val qualityJson: String?,
)

@Entity(
    tableName = "captures",
    foreignKeys = [ForeignKey(FieldEntity::class, ["id"], ["field_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("field_id")],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "field_id") val fieldId: Long,
    /** Ruta relativa a la carpeta de capturas, con la foto original sin cajas. */
    val path: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    val attempt: Int,
)

@Entity(
    tableName = "inferences",
    foreignKeys = [ForeignKey(CaptureEntity::class, ["id"], ["capture_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("capture_id")],
)
data class InferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "capture_id") val captureId: Long,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "base_conf") val baseConf: Float,
    val conf: Float,
    @ColumnInfo(name = "nms_iou") val nmsIou: Float,
    @ColumnInfo(name = "count_strict") val countStrict: Int,
    @ColumnInfo(name = "count_broad") val countBroad: Int,
    /** Detecciones crudas con puntaje, para volver a filtrar sin reejecutar el modelo. */
    @ColumnInfo(name = "detections_json") val detectionsJson: String,
    @ColumnInfo(name = "t_pre_ms") val tPreMs: Double,
    @ColumnInfo(name = "t_infer_ms") val tInferMs: Double,
    @ColumnInfo(name = "t_post_ms") val tPostMs: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
