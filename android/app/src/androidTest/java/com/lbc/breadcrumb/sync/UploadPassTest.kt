package com.lbc.breadcrumb.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryDao
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.net.EnrichmentReply
import com.lbc.breadcrumb.net.IngestResult
import com.lbc.breadcrumb.net.Reply
import com.lbc.breadcrumb.net.UploadApi
import com.lbc.breadcrumb.net.UploadMemory
import com.lbc.breadcrumb.net.UploadPicture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * The upload pass against an in-memory Room database on the device, with a
 * fake server that records what it was sent.
 */
@RunWith(AndroidJUnit4::class)
class UploadPassTest {

    private lateinit var db: BreadcrumbDatabase
    private lateinit var memoryDao: MemoryDao
    private val api = FakeApi()

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        memoryDao = db.memoryDao()
    }

    @After
    fun close() = db.close()

    private fun pass(
        cloudAllowed: Boolean = true,
        held: Collection<String> = emptyList(),
        reading: Boolean = false,
        readLinks: suspend () -> Unit = {},
    ) = UploadPass(
        dao = db.uploadDao(),
        api = api,
        cloudAllowed = { cloudAllowed },
        held = { held },
        reading = { reading },
        pictureOf = { PICTURE },
        readLinks = readLinks,
    )

    private suspend fun state(id: String) = memoryDao.getById(id)!!.syncState

    private fun text(id: String, capturedAt: Long) = Memory(id = id, type = MemoryType.TEXT, rawText = "words of $id", capturedAt = capturedAt)

    @Test
    fun deletesGoFirstThenMemoriesOldestFirstTenAtATimeThenTheirSummaries() = runBlocking {
        val gone = text("gone", 0)
        memoryDao.upsert(gone)
        memoryDao.deleteEverywhere(gone, now = 1)
        memoryDao.upsertAll((1..23).map { text("m%02d".format(it), it.toLong()) })

        assertTrue(pass().run() is UploadPass.Outcome.Done)

        assertEquals(listOf("delete", "memories", "memories", "memories", "enrichment"), api.calls.map { it.substringBefore(' ') })
        assertEquals((1..23).map { "m%02d".format(it) }, api.sent.map { it.id })
        assertEquals(listOf(10, 10, 3), api.batches)
        assertEquals(0, memoryDao.pendingDeleteCount())
        assertEquals(SyncState.SYNCED, state("m23"))
        assertEquals("summary of m01", memoryDao.getById("m01")!!.summary)
    }

    @Test
    fun aLinksPageIsReadAfterDeletesAndBeforeItsMemoryGoes() = runBlocking {
        val gone = text("gone", 0)
        memoryDao.upsert(gone)
        memoryDao.deleteEverywhere(gone, now = 1)
        memoryDao.upsert(Memory(id = "link", type = MemoryType.LINK, rawText = "https://example.com/job", capturedAt = 1))

        pass(readLinks = {
            api.calls += "links"
            db.readingDao().updateLinkReading("link", "Apply by April 30", "Intern, Qualcomm")
        }).run()

        assertEquals(listOf("delete", "links", "memories"), api.calls.map { it.substringBefore(' ') }.take(3))
        // Sent once, with its page's words.
        assertEquals(listOf("Apply by April 30"), api.sent.map { it.extractedText })
        assertEquals("Intern, Qualcomm", api.sent.single().title)
    }

    @Test
    fun aNoteThatLandsMidUploadIsSentAgainNotMarkedSent() = runBlocking {
        memoryDao.upsert(text("m", 1))
        var first = true
        api.onMemories = { batch ->
            if (first) db.captureDao().setNote(listOf("m"), "Priya says apply early")
            first = false
            api.finished(batch)
        }

        pass().run()

        assertEquals(listOf(null, "Priya says apply early"), api.sent.map { it.note })
        assertEquals(SyncState.SYNCED, state("m"))
    }

    @Test
    fun anUploadALostProcessLeftIsSentAgain() = runBlocking {
        memoryDao.upsert(text("m", 1).copy(syncState = SyncState.UPLOADING))

        pass().run()

        assertEquals(listOf("m"), api.sent.map { it.id })
        assertEquals(SyncState.SYNCED, state("m"))
    }

    @Test
    fun heldMemoriesAndUnreadPicturesWait() = runBlocking {
        val now = System.currentTimeMillis()
        memoryDao.upsertAll(
            listOf(
                text("held", 1),
                Memory(id = "unread", type = MemoryType.IMAGE, localUri = "file:///x.jpg", capturedAt = now),
                text("free", 2),
            ),
        )

        val outcome = pass(held = listOf("held"), reading = true).run()

        assertEquals(listOf("free"), api.sent.map { it.id })
        assertEquals(SyncState.PENDING, state("held"))
        assertEquals(SyncState.PENDING, state("unread"))
        // Looked at again once the unread picture's grace is over.
        val recheckAt = (outcome as UploadPass.Outcome.Done).recheckAt
        assertNotNull(recheckAt)
        assertTrue(recheckAt!! >= now + UploadPass.GRACE_MILLIS)
    }

    @Test
    fun a503KeepsTheUnfinishedOnesQueuedAndStopsThePass() = runBlocking {
        memoryDao.upsertAll(listOf(text("a", 1), text("b", 2)))
        api.onMemories = {
            Reply.Later(listOf(IngestResult("a", enriched = true, embedded = true), IngestResult("b", reason = "rate limited", retryable = true)), "HTTP 503")
        }

        val outcome = pass().run()

        assertEquals(UploadPass.Outcome.Later("HTTP 503"), outcome)
        assertEquals(SyncState.SYNCED, state("a"))
        assertEquals(SyncState.PENDING, state("b"))
        assertEquals(1, api.calls.size)
    }

    @Test
    fun noAnswerLeavesEverythingQueued() = runBlocking {
        memoryDao.upsertAll(listOf(text("a", 1), text("b", 2)))
        api.onMemories = { Reply.Later(null, "Refused") }

        assertTrue(pass().run() is UploadPass.Outcome.Later)

        assertEquals(SyncState.PENDING, state("a"))
        assertEquals(SyncState.PENDING, state("b"))
    }

    @Test
    fun aRefusalFailsOnlyTheMemoriesItNames() = runBlocking {
        memoryDao.upsertAll(listOf(text("a", 1), text("bad", 2), text("c", 3)))
        api.onMemories = { batch ->
            if (batch.any { it.id == "bad" }) Reply.Refused(400, listOf(batch.indexOfFirst { it.id == "bad" })) else api.finished(batch)
        }

        assertTrue(pass().run() is UploadPass.Outcome.Done)

        assertEquals(SyncState.FAILED, state("bad"))
        assertEquals(SyncState.SYNCED, state("a"))
        assertEquals(SyncState.SYNCED, state("c"))
    }

    @Test
    fun picturesNeverGoSettingOrNot() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "photo", type = MemoryType.IMAGE, localUri = "file:///p.jpg", extractedText = "", capturedAt = 1),
                Memory(id = "screenshot", type = MemoryType.IMAGE, localUri = "file:///s.jpg", extractedText = "words ".repeat(40), capturedAt = 2),
            ),
        )

        pass().run()

        // Memories and enrichment go, but pictures never go (local-first L4)
        assertEquals(emptyList<UploadPicture>(), api.pictures)
        assertEquals(listOf("memories", "enrichment"), api.calls.map { it.substringBefore(' ') })
        assertNull(memoryDao.getById("photo")!!.imageSentAt)
    }

    @Test
    fun whileSettingIsOffUploadPassSendsNoMemoriesNoPicturesAndDeletesStillGo() = runBlocking {
        val gone = text("gone", 0)
        memoryDao.upsert(gone)
        memoryDao.deleteEverywhere(gone, now = 1)
        memoryDao.upsertAll(listOf(text("m1", 1), text("m2", 2)))
        var linksRead = false

        val outcome = pass(cloudAllowed = false, readLinks = { linksRead = true }).run()

        assertTrue(outcome is UploadPass.Outcome.Done)
        assertTrue(linksRead)
        // Deletes still go, but memories, pictures, and enrichment are never sent
        assertEquals(listOf("delete"), api.calls.map { it.substringBefore(' ') })
        assertEquals(0, memoryDao.pendingDeleteCount())
        assertEquals(emptyList<UploadMemory>(), api.sent)
        assertEquals(emptyList<UploadPicture>(), api.pictures)
        assertEquals(SyncState.PENDING, state("m1"))
        assertEquals(SyncState.PENDING, state("m2"))
    }

    @Test
    fun savesFromSensitiveAppsNeverGo() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "gpay", type = MemoryType.TEXT, sourceApp = "com.google.android.apps.nbu.paisa.user", rawText = "paid 500", capturedAt = 1),
                Memory(id = "pass", type = MemoryType.TEXT, sourceApp = "com.onepassword.android", rawText = "login creds", capturedAt = 2),
                Memory(id = "safe", type = MemoryType.TEXT, sourceApp = "com.whatsapp", rawText = "hello", capturedAt = 3),
            ),
        )

        pass().run()

        // Only safe memory was sent over network
        assertEquals(listOf("safe"), api.sent.map { it.id })
        // Sensitive memories are marked synced locally so they don't block the queue
        assertEquals(SyncState.SYNCED, state("gpay"))
        assertEquals(SyncState.SYNCED, state("pass"))
        assertEquals(SyncState.SYNCED, state("safe"))
    }

    @Test
    fun everySummaryAskedForIsMarkedCopiedSoThePassEnds() = runBlocking {
        memoryDao.upsertAll(listOf(text("a", 1), text("b", 2)))
        api.onEnrichment = { Reply.Done(emptyList()) }

        assertTrue(pass().run() is UploadPass.Outcome.Done)

        assertEquals(1, api.calls.count { it.startsWith("enrichment") })
        assertNull(memoryDao.getById("a")!!.summary)
        assertNotNull(memoryDao.getById("a")!!.enrichedAt)
    }

    private class FakeApi : UploadApi {
        val calls = mutableListOf<String>()
        val sent = mutableListOf<UploadMemory>()
        val batches = mutableListOf<Int>()
        val pictures = mutableListOf<UploadPicture>()

        fun finished(batch: List<UploadMemory>): Reply<List<IngestResult>> =
            Reply.Done(batch.map { IngestResult(it.id, enriched = true, embedded = true) })

        var onMemories: suspend (List<UploadMemory>) -> Reply<List<IngestResult>> = { finished(it) }
        var onEnrichment: suspend (List<String>) -> Reply<List<EnrichmentReply>> =
            { ids -> Reply.Done(ids.map { EnrichmentReply(it, "summary of $it", "note", null) }) }

        override suspend fun sendMemories(memories: List<UploadMemory>): Reply<List<IngestResult>> {
            calls += "memories ${memories.map { it.id }}"
            sent += memories
            batches += memories.size
            return onMemories(memories)
        }

        override suspend fun sendPictures(pictures: List<UploadPicture>): Reply<List<IngestResult>> {
            calls += "pictures ${pictures.map { it.id }}"
            this.pictures += pictures
            return Reply.Done(pictures.map { IngestResult(it.id, enriched = true, embedded = true) })
        }

        override suspend fun askEnrichment(ids: List<String>): Reply<List<EnrichmentReply>> {
            calls += "enrichment $ids"
            return onEnrichment(ids)
        }

        override suspend fun sendDeletes(ids: List<String>): Reply<Int> {
            calls += "delete $ids"
            return Reply.Done(ids.size)
        }
    }

    private companion object {
        val PICTURE = byteArrayOf(1, 2, 3, 4)
    }
}
