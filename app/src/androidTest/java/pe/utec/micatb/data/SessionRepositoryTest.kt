package pe.utec.micatb.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.utec.micatb.data.db.FieldStatus
import pe.utec.micatb.data.db.MicaDatabase
import pe.utec.micatb.data.db.SessionStatus

class SessionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MicaDatabase
    private lateinit var photos: File
    private lateinit var repo: SessionRepository

    @Before fun setUp() {
        photos = File(context.cacheDir, "repo-test-${System.nanoTime()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, MicaDatabase::class.java).build()
        repo = SessionRepository(db, photos)
    }

    @After fun tearDown() { db.close(); photos.deleteRecursively() }

    private fun inference(strict: Int = 3, broad: Int = 4) = InferenceInput("m", 0.25f, 0.45f, 0.5f, strict, broad, "[]", 1.0, 2.0, 3.0)

    private fun photo(attempt: Int = 1, bytes: ByteArray = byteArrayOf(1, 2, 3, 4)) = PhotoInput(PhotoSource.Bytes(bytes), 640, 480, attempt)

    private suspend fun newSession(fields: Int = 3): String {
        val id = repo.createSession("lámina 7", "m", "sha", "{}")
        repo.prepareFields(id, List(fields) { FieldPos(it, it * 10, 0) })
        return id
    }

    @Test fun validFieldStoresPhotoRowsAndSha(): Unit = runBlocking {
        val id = newSession()
        repo.record(id, 0, FieldOutcome(FieldStatus.VALID, 1, "{}", photo(), inference()))
        val d = repo.detail(id)!!
        val f = d.fields[0]
        assertEquals(FieldStatus.VALID.name, f.field.status)
        assertNotNull(f.capture); assertNotNull(f.inference)
        assertEquals(3, f.inference!!.countStrict)
        val file = repo.photoFile(f.capture!!)
        assertTrue(file.exists())
        assertEquals(4L, file.length())
        assertEquals(64, f.capture!!.sha256.length)
        assertEquals(FieldStatus.PENDING.name, d.fields[1].field.status)
    }

    @Test fun failureAfterWritingThePhotoLeavesNoOrphanFileAndNoRows(): Unit = runBlocking {
        val id = newSession()
        repo.beforeCommit = { error("fallo simulado") }
        try {
            repo.record(id, 0, FieldOutcome(FieldStatus.VALID, 1, null, photo(), inference()))
            error("debio fallar")
        } catch (e: IllegalStateException) {
            assertEquals("fallo simulado", e.message)
        }
        repo.beforeCommit = null
        assertEquals(0, File(photos, id).listFiles().orEmpty().size)
        val d = repo.detail(id)!!
        assertEquals(FieldStatus.PENDING.name, d.fields[0].field.status)
        assertEquals(null, d.fields[0].capture)
    }

    @Test fun reconcileRemovesStrayFilesTempFilesAndDeadSessionFolders(): Unit = runBlocking {
        val id = newSession()
        repo.record(id, 0, FieldOutcome(FieldStatus.VALID, 1, null, photo(), inference()))
        File(File(photos, id), "9-1.jpg").writeBytes(byteArrayOf(9))
        File(File(photos, id), "1-1.jpg.tmp").writeBytes(byteArrayOf(9))
        File(photos, "sesion-muerta").apply { mkdirs() }.let { File(it, "a.jpg").writeBytes(byteArrayOf(1)) }
        val removed = repo.reconcileOrphans()
        assertEquals(3, removed)
        assertTrue(File(File(photos, id), "0-1.jpg").exists())
        assertFalse(File(photos, "sesion-muerta").exists())
    }

    @Test fun resumeIndexPointsAtTheFirstFieldWithoutAFinalResult(): Unit = runBlocking {
        val id = newSession(fields = 5)
        assertEquals(0, repo.resumeIndex(id))
        repo.record(id, 0, FieldOutcome(FieldStatus.VALID, 1, null, photo(), inference()))
        repo.record(id, 1, FieldOutcome(FieldStatus.INVALID_QUALITY, 3, null, photo(3), null))
        repo.record(id, 2, FieldOutcome(FieldStatus.SKIPPED, 0, null))
        assertEquals(3, repo.resumeIndex(id))
        repo.record(id, 3, FieldOutcome(FieldStatus.FAILED_MOTION, 0, null))
        assertEquals(3, repo.resumeIndex(id)) // un movimiento fallido se vuelve a intentar
    }

    @Test fun summariesCountOnlyValidFields(): Unit = runBlocking {
        val id = newSession(fields = 4)
        repo.record(id, 0, FieldOutcome(FieldStatus.VALID, 1, null, photo(), inference(strict = 5, broad = 6)))
        repo.record(id, 1, FieldOutcome(FieldStatus.VALID, 1, null, photo(), inference(strict = 2, broad = 2)))
        repo.record(id, 2, FieldOutcome(FieldStatus.INVALID_QUALITY, 3, null, photo(3), inference(strict = 99, broad = 99)))
        val s = repo.observeSummaries().first().single()
        assertEquals(4, s.fields)
        assertEquals(2, s.validFields)
        assertEquals(7, s.countStrict)
        assertEquals(8, s.countBroad)
    }

    @Test fun unfinishedSessionsAreFoundForRecovery(): Unit = runBlocking {
        val a = newSession(); val b = newSession()
        repo.setStatus(a, SessionStatus.RUNNING)
        repo.setStatus(b, SessionStatus.COMPLETED)
        assertEquals(listOf(a), repo.unfinished().map { it.id })
        assertNotNull(repo.session(b)!!.finishedAt)
    }

    @Test fun dataSurvivesClosingAndReopeningTheDatabase(): Unit = runBlocking {
        val name = "reopen-${System.nanoTime()}.db"
        val dbDir = File(context.cacheDir, "reopen-photos-${System.nanoTime()}")
        var first = Room.databaseBuilder(context, MicaDatabase::class.java, name).build()
        var r = SessionRepository(first, dbDir)
        val id = r.createSession("persistente", "m", "sha", "{}")
        r.prepareFields(id, listOf(FieldPos(0, 0, 0)))
        r.record(id, 0, FieldOutcome(FieldStatus.VALID, 1, null, photo(), inference(strict = 8)))
        first.close()

        first = Room.databaseBuilder(context, MicaDatabase::class.java, name).build()
        r = SessionRepository(first, dbDir)
        val d = r.detail(id)!!
        assertEquals("persistente", d.session.sampleLabel)
        assertEquals(8, d.fields[0].inference!!.countStrict)
        assertTrue(r.photoFile(d.fields[0].capture!!).exists())
        first.close(); context.deleteDatabase(name); dbDir.deleteRecursively()
    }
}
