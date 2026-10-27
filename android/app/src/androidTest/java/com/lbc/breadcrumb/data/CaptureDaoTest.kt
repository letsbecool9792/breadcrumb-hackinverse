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
    fun anotherNoteGoesUnderTheNoteKeptAlready() = runBlocking {
        memories.upsertAll(
            listOf(
                Memory(id = "a", type = MemoryType.LINK, note = "from priya"),
                Memory(id = "b", type = MemoryType.LINK),
            ),
        )

        assertEquals(2, capture.addNote(listOf("a", "b"), "for the trip"))

        assertEquals("from priya\nfor the trip", memories.getById("a")?.note)
        assertEquals("for the trip", memories.getById("b")?.note)
    }

    @Test
    fun aSaveIsComparedWithMemoriesOfItsKindNewestFirst() = runBlocking {
        memories.upsertAll(
            listOf(
                Memory(id = "old", type = MemoryType.TEXT, capturedAt = 1_000),
                Memory(id = "new", type = MemoryType.TEXT, capturedAt = 2_000),
                Memory(id = "link", type = MemoryType.LINK, capturedAt = 3_000),
            ),
        )

        assertEquals(listOf("new", "old"), capture.ofType(MemoryType.TEXT).map { it.id })
    }

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
    fun aChangedNotePutsASyncedMemoryBackInTheQueue() = runBlocking {
        memories.upsert(Memory(id = "s1", type = MemoryType.TEXT, rawText = "a link", syncState = SyncState.SYNCED))

        val changed = capture.setNote(listOf("s1"), "Priya says apply early")

        assertEquals(1, changed)
        assertEquals(SyncState.PENDING, memories.getById("s1")?.syncState)
    }

    @Test
    fun anUnchangedNoteChangesNothing() = runBlocking {
        memories.upsertAll(
            listOf(
                Memory(id = "same", type = MemoryType.TEXT, note = "from the trip", syncState = SyncState.SYNCED, updatedAt = 1_000L),
                Memory(id = "none", type = MemoryType.TEXT, note = null, syncState = SyncState.SYNCED, updatedAt = 1_000L),
            ),
        )

        assertEquals(0, capture.setNote(listOf("same"), "from the trip"))
        assertEquals(0, capture.setNote(listOf("none"), null))
        assertEquals(0, capture.setNote(listOf("none"), ""))

        for (id in listOf("same", "none")) {
            val after = memories.getById(id)
            assertEquals(SyncState.SYNCED, after?.syncState)
            assertEquals(1_000L, after?.updatedAt)
        }
    }

    @Test
    fun aNoteIsFoundByItsWords() = runBlocking {
        memories.upsert(Memory(id = "t1", type = MemoryType.TEXT, rawText = "a link"))

        capture.setNote(listOf("t1"), "brewery recommendation")

        assertEquals(1, matches("brewery"))
    }

    @Test
    fun aNullNoteClearsAnExistingNoteAndRequeuesIt() = runBlocking {
        memories.upsert(
            Memory(
                id = "clear1",
                type = MemoryType.TEXT,
                rawText = "note content",
                note = "existing note",
                syncState = SyncState.SYNCED,
            ),
        )

        val changed = capture.setNote(listOf("clear1"), null)

        assertEquals(1, changed)
        val after = memories.getById("clear1")
        assertEquals(null, after?.note)
        assertEquals(SyncState.PENDING, after?.syncState)
    }

    private fun matches(word: String): Int =
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM memories_fts WHERE memories_fts MATCH ?", arrayOf<Any?>("\"$word*\""))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
