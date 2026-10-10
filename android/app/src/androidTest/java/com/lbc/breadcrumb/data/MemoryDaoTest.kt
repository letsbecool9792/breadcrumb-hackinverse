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
 * The core DAO against a real SQLite on the device, in memory, so it never
 * touches the app's own database.
 */
@RunWith(AndroidJUnit4::class)
class MemoryDaoTest {

    private lateinit var db: BreadcrumbDatabase
    private lateinit var dao: MemoryDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        dao = db.memoryDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun upsertingTheSameIdReplacesRatherThanDuplicates() = runBlocking {
        val memory = Memory(id = "m1", type = MemoryType.TEXT, rawText = "first")
        dao.upsert(memory)
        dao.upsert(memory.copy(rawText = "second"))

        assertEquals(1, dao.count())
        assertEquals("second", dao.getById("m1")?.rawText)
    }

    @Test
    fun upsertKeepsTheWordIndexInStep() = runBlocking {
        val memory = Memory(id = "m1", type = MemoryType.TEXT, rawText = "brewery burger")
        dao.upsert(memory)
        assertEquals(1, matches("brewery"))

        dao.upsert(memory.copy(rawText = "dosa place"))
        assertEquals("the old words must leave the index", 0, matches("brewery"))
        assertEquals(1, matches("dosa"))
    }

    @Test
    fun deleteEverywhereRemovesTheRowAndQueuesTheServersDelete() = runBlocking {
        val memory = Memory(id = "m1", type = MemoryType.TEXT, rawText = "to delete")
        dao.upsert(memory)

        dao.deleteEverywhere(memory, now = 1_000)

        assertNull(dao.getById("m1"))
        assertEquals(1, dao.pendingDeleteCount())
        assertEquals("a deleted memory's words leave the index", 0, matches("delete"))
    }

    @Test
    fun restoreUndoesADelete() = runBlocking {
        val memory = Memory(id = "m1", type = MemoryType.TEXT, rawText = "kept after all")
        dao.upsert(memory)
        dao.deleteEverywhere(memory, now = 1_000)

        dao.restore(memory.copy(syncState = SyncState.PENDING))

        assertEquals("kept after all", dao.getById("m1")?.rawText)
        assertEquals(0, dao.pendingDeleteCount())
    }

    @Test
    fun clearEverywhereQueuesADeleteForEveryMemory() = runBlocking {
        dao.upsertAll(
            listOf(
                Memory(id = "a", type = MemoryType.TEXT),
                Memory(id = "b", type = MemoryType.LINK, hasLink = true),
            ),
        )

        dao.clearEverywhere(now = 1_000)

        assertEquals(0, dao.count())
        assertEquals(2, dao.pendingDeleteCount())
    }

    @Test
    fun newestFirst() = runBlocking {
        dao.upsertAll(
            listOf(
                Memory(id = "old", type = MemoryType.TEXT, capturedAt = 1_000),
                Memory(id = "new", type = MemoryType.TEXT, capturedAt = 2_000),
            ),
        )
        assertEquals(listOf("new", "old"), dao.getAll().map { it.id })
    }

    private fun matches(word: String): Int =
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM memories_fts WHERE memories_fts MATCH ?", arrayOf<Any?>("\"$word*\""))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}
