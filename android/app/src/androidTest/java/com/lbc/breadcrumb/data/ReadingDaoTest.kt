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

    private fun matchesFts(word: String): Int =
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM memories_fts WHERE memories_fts MATCH ?", arrayOf<Any?>("\"$word*\""))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
