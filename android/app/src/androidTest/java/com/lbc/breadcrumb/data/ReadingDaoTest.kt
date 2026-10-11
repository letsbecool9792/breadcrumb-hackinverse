package com.lbc.breadcrumb.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ReadingDao against an in-memory Room database on the device.
 */
@RunWith(AndroidJUnit4::class)
class ReadingDaoTest {

    private lateinit var db: BreadcrumbDatabase
    private lateinit var memoryDao: MemoryDao
    private lateinit var readingDao: ReadingDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        memoryDao = db.memoryDao()
        readingDao = db.readingDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun unreadImagesAreReturnedNewestFirst() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "old", type = MemoryType.IMAGE, capturedAt = 1_000, extractedText = null),
                Memory(id = "new", type = MemoryType.IMAGE, capturedAt = 2_000, extractedText = null),
                Memory(id = "text", type = MemoryType.TEXT, capturedAt = 3_000, extractedText = null),
            ),
        )

        val unread = readingDao.getUnreadImages()
        assertEquals(listOf("new", "old"), unread.map { it.id })
        assertEquals(2, readingDao.countUnreadImages())
    }

    @Test
    fun imagesWithExtractedTextAlreadySetAreNotUnread() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "read_text", type = MemoryType.IMAGE, extractedText = "Found text"),
                Memory(id = "read_empty", type = MemoryType.IMAGE, extractedText = ""),
                Memory(id = "unread", type = MemoryType.IMAGE, extractedText = null),
            ),
        )

        val unread = readingDao.getUnreadImages()
        assertEquals(listOf("unread"), unread.map { it.id })
        assertEquals(1, readingDao.countUnreadImages())
    }

    @Test
    fun targetedUpdateWritesExtractedTextAndKeepsFullTextIndexInStep() = runBlocking {
        val memory = Memory(id = "m1", type = MemoryType.IMAGE, title = "Qualcomm flyer")
        memoryDao.upsert(memory)

        val updated = readingDao.updateExtractedText("m1", "internship deadline April 30")
        assertEquals(1, updated)

        val stored = readingDao.getById("m1")
        assertEquals("internship deadline April 30", stored?.extractedText)

        // Verifies SQLite triggers fired to update memories_fts
        assertEquals(1, matchesFts("internship"))
        assertEquals(1, matchesFts("deadline"))
    }

    @Test
    fun aMemoryDeletedMidReadStaysDeletedTargetedUpdateReturnsZeroAndDoesNotResurrectRow() = runBlocking {
        val memory = Memory(id = "m1", type = MemoryType.IMAGE)
        memoryDao.upsert(memory)

        // Simulates user deleting row while OCR was computing
        memoryDao.deleteEverywhere(memory, now = 1_000)

        // Targeted update must match 0 rows and not re-insert the deleted row
        val updated = readingDao.updateExtractedText("m1", "some text")
        assertEquals(0, updated)
        assertNull(readingDao.getById("m1"))
        assertNull(memoryDao.getById("m1"))
    }

    @Test
    fun targetedUpdateDoesNotOverwriteRowIfAlreadyRead() = runBlocking {
        val memory = Memory(id = "m1", type = MemoryType.IMAGE, extractedText = "initial text")
        memoryDao.upsert(memory)

        val updated = readingDao.updateExtractedText("m1", "new text")
        assertEquals(0, updated)
        assertEquals("initial text", readingDao.getById("m1")?.extractedText)
    }

    @Test
    fun observeUnreadImagesEmitsUpdatesWhenNewUnreadImageIsInserted() = runBlocking {
        val initial = readingDao.observeUnreadImages().first()
        assertTrue(initial.isEmpty())

        memoryDao.upsert(Memory(id = "img1", type = MemoryType.IMAGE, extractedText = null))
        val afterInsert = readingDao.observeUnreadImages().first()
        assertEquals(listOf("img1"), afterInsert.map { it.id })
    }

    @Test
    fun wordInsideScreenshotFindsItViaExtractedText() = runBlocking {
        val screenshot = Memory(
            id = "shot1",
            type = MemoryType.IMAGE,
            title = null,
            rawText = null,
            extractedText = "Software Engineering Intern Qualcomm Bengaluru",
        )
        memoryDao.upsert(screenshot)

        val results = readingDao.search("intern")
        assertEquals(1, results.size)
        assertEquals("shot1", results[0].id)

        val multiTerm = readingDao.search("qualcomm bengaluru")
        assertEquals(1, multiTerm.size)
        assertEquals("shot1", multiTerm[0].id)
    }

    @Test
    fun searchOrdersNewestFirst() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "older", type = MemoryType.TEXT, rawText = "internship note", capturedAt = 1000),
                Memory(id = "newer", type = MemoryType.TEXT, rawText = "internship offer", capturedAt = 5000),
            ),
        )

        val results = readingDao.search("internship")
        assertEquals(listOf("newer", "older"), results.map { it.id })
    }

    @Test
    fun searchFoldsCaseAndAccents() = runBlocking {
        val memory = Memory(
            id = "c1",
            type = MemoryType.TEXT,
            rawText = "Meet at Café Coffee Day on MG Road",
        )
        memoryDao.upsert(memory)

        val lowerNoAccent = readingDao.search("cafe")
        assertEquals(1, lowerNoAccent.size)
        assertEquals("c1", lowerNoAccent[0].id)

        val withAccent = readingDao.search("Café")
        assertEquals(1, withAccent.size)
        assertEquals("c1", withAccent[0].id)
    }

    @Test
    fun specialCharactersNeverBreakTheSearch() = runBlocking {
        val memory = Memory(
            id = "m1",
            type = MemoryType.TEXT,
            rawText = "Qualcomm internship in April",
        )
        memoryDao.upsert(memory)

        // Quotes, hyphens, OR, column filter syntax must not throw SQLite exceptions
        assertEquals(1, readingDao.search("\"qualcomm\"").size)
        assertEquals(1, readingDao.search("qualcomm-internship").size)
        // OR is a word to find, never an operator: as an operator it would match
        // on "qualcomm" alone; as a word, nothing here starts with "or" or "banana".
        assertEquals(0, readingDao.search("qualcomm OR banana").size)
        // Nor is column: a filter: as one it would find the memory by its rawText;
        // as words, nothing here starts with "rawtext".
        assertEquals(0, readingDao.search("rawText:qualcomm").size)
        assertEquals(0, readingDao.search("\"").size)
        assertEquals(0, readingDao.search("-").size)
        assertEquals(0, readingDao.search(":::").size)
        assertEquals(0, readingDao.search("").size)
    }

    @Test
    fun observeSearchEmitsLiveResults() = runBlocking {
        val flow = readingDao.observeSearch("burger")
        assertEquals(0, flow.first().size)

        memoryDao.upsert(Memory(id = "b1", type = MemoryType.TEXT, rawText = "Brewery burger indiranagar"))
        val afterInsert = flow.first()
        assertEquals(1, afterInsert.size)
        assertEquals("b1", afterInsert[0].id)
    }

    @Test
    fun searchRespectsLimit() = runBlocking {
        for (i in 1..10) {
            memoryDao.upsert(Memory(id = "item$i", type = MemoryType.TEXT, rawText = "hackathon project $i", capturedAt = i.toLong()))
        }

        val results = readingDao.search("hackathon", limit = 3)
        assertEquals(3, results.size)
        assertEquals(listOf("item10", "item9", "item8"), results.map { it.id })
    }

    @Test
    fun aSyncedPictureReadWithTextGoesBackToPending() = runBlocking {
        memoryDao.upsert(Memory(id = "p1", type = MemoryType.IMAGE, syncState = SyncState.SYNCED))

        readingDao.updateExtractedText("p1", "Qualcomm internship, apply by April 30")

        assertEquals(SyncState.PENDING, memoryDao.getById("p1")?.syncState)
    }

    @Test
    fun anEmptyReadLeavesASyncedPictureAlone() = runBlocking {
        memoryDao.upsert(Memory(id = "p2", type = MemoryType.IMAGE, syncState = SyncState.SYNCED))

        readingDao.updateExtractedText("p2", "")

        val after = memoryDao.getById("p2")
        assertEquals("", after?.extractedText)
        assertEquals(SyncState.SYNCED, after?.syncState)
    }

    @Test
    fun unreadMediaIncludesBothImagesAndPdfsNewestFirst() = runBlocking {
        memoryDao.upsertAll(
            listOf(
                Memory(id = "img1", type = MemoryType.IMAGE, capturedAt = 1_000, extractedText = null),
                Memory(id = "pdf1", type = MemoryType.PDF, capturedAt = 2_000, extractedText = null),
                Memory(id = "text1", type = MemoryType.TEXT, capturedAt = 3_000, extractedText = null),
                Memory(id = "pdf_read", type = MemoryType.PDF, capturedAt = 4_000, extractedText = "already read"),
            ),
        )

        assertEquals(listOf("pdf1"), readingDao.getNewestUnreadMedia(1).map { it.id })
        assertEquals(listOf("pdf1", "img1"), readingDao.getNewestUnreadMedia(10).map { it.id })

        val unread = readingDao.observeUnreadMedia().first()
        assertEquals(listOf("pdf1", "img1"), unread.map { it.id })
    }

    private fun matchesFts(word: String): Int =
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM memories_fts WHERE memories_fts MATCH ?", arrayOf<Any?>("\"$word*\""))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
