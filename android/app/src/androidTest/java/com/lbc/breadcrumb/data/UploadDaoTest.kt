package com.lbc.breadcrumb.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UploadDao against an in-memory Room database on the device.
 */
@RunWith(AndroidJUnit4::class)
class UploadDaoTest {

    private lateinit var db: BreadcrumbDatabase
    private lateinit var memoryDao: MemoryDao
    private lateinit var dao: UploadDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        memoryDao = db.memoryDao()
        dao = db.uploadDao()
    }

    @After
    fun close() = db.close()

    private suspend fun state(id: String) = memoryDao.getById(id)!!.syncState

    private suspend fun claimAll(held: List<String> = emptyList(), reading: Boolean = false, unreadSince: Long = Long.MAX_VALUE) =
        dao.claimNext(held, reading, unreadSince, 50).map { it.id }

    @Test
    fun markingSyncedMovesOnlyUploadingRows() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "sent", type = MemoryType.TEXT, syncState = SyncState.UPLOADING),
                Memory(id = "changed", type = MemoryType.TEXT, syncState = SyncState.PENDING),
                Memory(id = "refused", type = MemoryType.TEXT, syncState = SyncState.FAILED),
            ),
        )

        dao.markSynced(listOf("sent", "changed", "refused"))

        assertEquals(SyncState.SYNCED, state("sent"))
        assertEquals(SyncState.PENDING, state("changed"))
        assertEquals(SyncState.FAILED, state("refused"))
    }

    @Test
    fun aNoteOrAReadThatLandsMidUploadStaysPending() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "noted", type = MemoryType.TEXT, rawText = "words"),
                Memory(id = "read", type = MemoryType.IMAGE, extractedText = null),
            ),
        )
        assertEquals(listOf("noted", "read"), claimAll().sorted())

        db.captureDao().setNote(listOf("noted"), "from Priya")
        db.readingDao().updateExtractedText("read", "Qualcomm is hiring")
        dao.markSynced(listOf("noted", "read"))

        assertEquals(SyncState.PENDING, state("noted"))
        assertEquals(SyncState.PENDING, state("read"))
        assertEquals(listOf("noted", "read"), claimAll().sorted())
    }

    @Test
    fun aStaleUploadingRowGoesBackToPending() = runBlocking {
        memoryDao.upsert(Memory(id = "left", type = MemoryType.TEXT, syncState = SyncState.UPLOADING))

        assertEquals(1, dao.releaseStale())

        assertEquals(SyncState.PENDING, state("left"))
    }

    @Test
    fun claimingTakesTheOldestFirstAndSkipsHeldIds() = runBlocking {
        memoryDao.upsertAll(
            (1..12).map { Memory(id = "m$it", type = MemoryType.TEXT, capturedAt = it.toLong()) } +
                Memory(id = "synced", type = MemoryType.TEXT, capturedAt = 0, syncState = SyncState.SYNCED),
        )

        val first = dao.claimNext(held = listOf("m1", "m2"), reading = false, unreadSince = Long.MAX_VALUE, limit = 10).map { it.id }

        assertEquals((3..12).map { "m$it" }, first)
        assertEquals(SyncState.UPLOADING, state("m3"))
        assertEquals(SyncState.PENDING, state("m1"))
        assertEquals(emptyList<String>(), claimAll(held = listOf("m1", "m2")))
    }

    @Test
    fun unreadPicturesAndPdfsWaitWhileReadingAndThroughTheGrace() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "old picture", type = MemoryType.IMAGE, capturedAt = 1_000, extractedText = null),
                Memory(id = "new pdf", type = MemoryType.PDF, capturedAt = 5_000, extractedText = null),
                Memory(id = "read picture", type = MemoryType.IMAGE, capturedAt = 5_000, extractedText = ""),
                Memory(id = "link", type = MemoryType.LINK, capturedAt = 5_000, extractedText = null),
            ),
        )

        assertEquals(listOf("link", "read picture"), dao.nextToSend(emptyList(), reading = true, unreadSince = 9_000, limit = 10).map { it.id }.sorted())
        assertEquals(
            listOf("link", "old picture", "read picture"),
            dao.nextToSend(emptyList(), reading = false, unreadSince = 2_000, limit = 10).map { it.id }.sorted(),
        )
        assertEquals(4, dao.nextToSend(emptyList(), reading = false, unreadSince = 5_000, limit = 10).size)
        assertEquals(1_000L, dao.oldestUnreadWaiting())
    }

    @Test
    fun picturesGoOnlyWhenOcrReadLittleOnceAndAfterTheirMemory() = runBlocking {
        val uri = "file:///originals/x.jpg"
        val long = "x".repeat(80)
        memoryDao.upsertAll(
            listOf(
                Memory(id = "photo", type = MemoryType.IMAGE, localUri = uri, extractedText = "", syncState = SyncState.SYNCED),
                Memory(id = "few words", type = MemoryType.IMAGE, localUri = uri, extractedText = "x".repeat(79), syncState = SyncState.SYNCED),
                Memory(id = "screenshot", type = MemoryType.IMAGE, localUri = uri, extractedText = long, syncState = SyncState.SYNCED),
                Memory(id = "not sent yet", type = MemoryType.IMAGE, localUri = uri, extractedText = "", syncState = SyncState.PENDING),
                Memory(id = "already", type = MemoryType.IMAGE, localUri = uri, extractedText = "", imageSentAt = 1, syncState = SyncState.SYNCED),
                Memory(id = "unread", type = MemoryType.IMAGE, localUri = uri, capturedAt = 1_000, syncState = SyncState.SYNCED),
            ),
        )

        assertEquals(listOf("few words", "photo"), dao.picturesToSend(80, reading = true, unreadSince = 9_000, limit = 10).map { it.id }.sorted())
        assertEquals(listOf("few words", "photo", "unread"), dao.picturesToSend(80, reading = false, unreadSince = 1_000, limit = 10).map { it.id }.sorted())

        dao.markPicturesSent(listOf("few words", "photo", "unread"), now = 2_000)

        assertEquals(emptyList<String>(), dao.picturesToSend(80, reading = false, unreadSince = 9_000, limit = 10).map { it.id })
    }

    @Test
    fun sendingClearsTheCopyOfTheServersReading() = runBlocking {
        memoryDao.upsert(Memory(id = "m", type = MemoryType.TEXT, enrichedAt = 1_000, syncState = SyncState.UPLOADING))

        dao.markSynced(listOf("m"))

        assertNull(memoryDao.getById("m")!!.enrichedAt)
        assertEquals(listOf("m"), dao.toEnrich(50))
    }

    @Test
    fun everyIdAskedIsMarkedCopiedAnsweredOrNot() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "answered", type = MemoryType.TEXT, syncState = SyncState.SYNCED),
                Memory(id = "absent", type = MemoryType.TEXT, syncState = SyncState.SYNCED),
            ),
        )

        dao.copyEnrichment(
            asked = listOf("answered", "absent"),
            readings = listOf(ServerReading("answered", "A job posting at Qualcomm", "job posting", null)),
            now = 5_000,
        )

        val answered = memoryDao.getById("answered")!!
        assertEquals("A job posting at Qualcomm", answered.summary)
        assertEquals("job posting", answered.kind)
        assertEquals(5_000L, answered.enrichedAt)
        assertEquals(5_000L, memoryDao.getById("absent")!!.enrichedAt)
        assertEquals(emptyList<String>(), dao.toEnrich(50))
        // The summary is found by local word search, offline.
        assertEquals(listOf("answered"), db.readingDao().search("qualcomm").map { it.id })
    }

    @Test
    fun refusedMemoriesGoBackInTheQueueOnlyWhenAsked() = runBlocking {
        memoryDao.upsert(Memory(id = "m", type = MemoryType.TEXT, syncState = SyncState.UPLOADING))

        dao.markFailed(listOf("m"))
        assertEquals(emptyList<String>(), claimAll())
        assertEquals(1, dao.retryFailed())

        assertEquals(listOf("m"), claimAll())
    }

    @Test
    fun deletesAreForgottenOnceSent() = runBlocking {
        val memory = Memory(id = "m", type = MemoryType.TEXT)
        memoryDao.upsert(memory)
        memoryDao.deleteEverywhere(memory, now = 1_000)

        assertEquals(listOf("m"), dao.pendingDeletes(100))
        dao.forgetDeletes(listOf("m"))

        assertEquals(emptyList<String>(), dao.pendingDeletes(100))
    }
}
