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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Notes said over a memory, against a real SQLite in memory, never the app's own database. */
@RunWith(AndroidJUnit4::class)
class SpokenNoteDaoTest {

    private lateinit var db: BreadcrumbDatabase
    private lateinit var memories: MemoryDao
    private lateinit var spoken: SpokenNoteDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        memories = db.memoryDao()
        spoken = db.spokenNoteDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun whatWasSaidGoesUnderTheNoteOnceAndIsFoundByItsWords() = runBlocking {
        memories.upsert(Memory(id = "photo", type = MemoryType.IMAGE, note = "from priya"))
        spoken.upsert(SpokenNote(memoryId = "photo", recordedAt = 1_000))
        assertEquals(listOf("photo"), spoken.observeUnheard().first())

        assertEquals(1, spoken.heard("photo", " the flat with the balcony "))
        // Heard once: a second word from a late listener changes nothing.
        assertEquals(0, spoken.heard("photo", "again"))

        assertEquals("from priya\nthe flat with the balcony", memories.getById("photo")?.note)
        assertEquals(emptyList<String>(), spoken.observeUnheard().first())
        assertEquals(listOf("photo"), db.readingDao().search("balcony").map { it.id })
    }

    @Test
    fun nothingSaidLeavesTheNoteAsItWas() = runBlocking {
        memories.upsert(Memory(id = "photo", type = MemoryType.IMAGE))
        spoken.upsert(SpokenNote(memoryId = "photo", recordedAt = 1_000))

        assertEquals(1, spoken.heard("photo", ""))

        assertNull(memories.getById("photo")?.note)
    }

    @Test
    fun aSpokenNoteGoesWithItsMemory() = runBlocking {
        val photo = Memory(id = "photo", type = MemoryType.IMAGE)
        memories.upsert(photo)
        spoken.upsert(SpokenNote(memoryId = "photo", recordedAt = 1_000))

        memories.delete(photo)

        assertNull(spoken.get("photo"))
    }
}
