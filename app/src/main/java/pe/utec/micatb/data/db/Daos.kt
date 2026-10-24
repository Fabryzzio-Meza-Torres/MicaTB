package pe.utec.micatb.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class FieldStats(val sessionId: String, val fields: Int, val valid: Int)

data class CountStats(val sessionId: String, val strict: Int, val broad: Int)

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: SessionEntity)

    @Update suspend fun update(s: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun get(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY created_at DESC") fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY created_at DESC")
    suspend fun unfinished(): List<SessionEntity>

    @Query("UPDATE sessions SET status = :status, finished_at = :finishedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, finishedAt: Long?)

    @Query("UPDATE sessions SET notes = :notes WHERE id = :id") suspend fun setNotes(id: String, notes: String?)

    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface FieldDao {
    @Insert suspend fun insert(f: FieldEntity): Long

    @Update suspend fun update(f: FieldEntity)

    @Query("SELECT * FROM fields WHERE session_id = :sid AND idx = :idx") suspend fun get(sid: String, idx: Int): FieldEntity?

    @Query("SELECT * FROM fields WHERE session_id = :sid ORDER BY idx") suspend fun list(sid: String): List<FieldEntity>

    @Query("SELECT * FROM fields WHERE session_id = :sid ORDER BY idx") fun observe(sid: String): Flow<List<FieldEntity>>

    @Query("SELECT session_id AS sessionId, COUNT(*) AS fields, SUM(CASE WHEN status = 'VALID' THEN 1 ELSE 0 END) AS valid FROM fields GROUP BY session_id")
    fun observeStats(): Flow<List<FieldStats>>
}

@Dao
interface CaptureDao {
    @Insert suspend fun insert(c: CaptureEntity): Long

    @Query("SELECT c.* FROM captures c JOIN fields f ON c.field_id = f.id WHERE f.session_id = :sid ORDER BY f.idx, c.attempt")
    suspend fun listForSession(sid: String): List<CaptureEntity>

    @Query("SELECT path FROM captures c JOIN fields f ON c.field_id = f.id WHERE f.session_id = :sid")
    suspend fun pathsForSession(sid: String): List<String>
}

@Dao
interface InferenceDao {
    @Insert suspend fun insert(i: InferenceEntity): Long

    @Query("SELECT i.* FROM inferences i JOIN captures c ON i.capture_id = c.id JOIN fields f ON c.field_id = f.id WHERE f.session_id = :sid")
    suspend fun listForSession(sid: String): List<InferenceEntity>

    @Query(
        "SELECT f.session_id AS sessionId, SUM(i.count_strict) AS strict, SUM(i.count_broad) AS broad " +
            "FROM inferences i JOIN captures c ON i.capture_id = c.id JOIN fields f ON c.field_id = f.id " +
            "WHERE f.status = 'VALID' GROUP BY f.session_id",
    )
    fun observeCounts(): Flow<List<CountStats>>
}

@Database(
    entities = [SessionEntity::class, FieldEntity::class, CaptureEntity::class, InferenceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MicaDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun fields(): FieldDao
    abstract fun captures(): CaptureDao
    abstract fun inferences(): InferenceDao
}
