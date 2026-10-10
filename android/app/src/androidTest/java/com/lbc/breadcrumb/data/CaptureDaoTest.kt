package com.lbc.breadcrumb.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The capture sheet's note against a real SQLite on the device, in memory, so
 * it never touches the app's own database.
 */
@RunWith(AndroidJUnit4::class)
class CaptureDaoTest {

    private lateinit var db: BreadcrumbDatabase
    private lateinit var memories: MemoryDao
    private lateinit var capture: CaptureDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        memories = db.memoryDao()
        capture = db.captureDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun aNoteKeepsTextReadAfterTheSave() = runBlocking {
        val saved = Memory(id = "p1", type = MemoryType.IMAGE)
        memories.upsert(saved)
        // The OCR queue reads the picture while the sheet is still open.
        db.readingDao().updateExtractedText("p1", "Qualcomm internship, apply by April 30")

        capture.setNote(listOf("p1"), "Priya says apply early")

        val after = memories.getById("p1")
        assertEquals("Priya says apply early", after?.note)
        assertEquals("Qualcomm internship, apply by April 30", after?.extractedText)
    }

    @Test
    fun aNoteGoesOnEveryMemoryOfTheShareAndNoOther() = runBlocking {
        memories.upsertAll(
            listOf(
                Memory(id = "a", type = MemoryType.IMAGE),
                Memory(id = "b", type = MemoryType.IMAGE),
                Memory(id = "other", type = MemoryType.TEXT, rawText = "unrelated"),
            ),
        )

        capture.setNote(listOf("a", "b"), "from the trip")

        assertEquals("from the trip", memories.getById("a")?.note)
        assertEquals("from the trip", memories.getById("b")?.note)
        assertEquals(null, memories.getById("other")?.note)
    }

    @Test
    fun aNoteIsFoundByItsWords() = runBlocking {
        memories.upsert(Memory(id = "t1", type = MemoryType.TEXT, rawText = "a link"))

        capture.setNote(listOf("t1"), "brewery recommendation")

        assertEquals(1, matches("brewery"))
    }

    private fun matches(word: String): Int =
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM memories_fts WHERE memories_fts MATCH ?", arrayOf<Any?>("\"$word*\""))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
