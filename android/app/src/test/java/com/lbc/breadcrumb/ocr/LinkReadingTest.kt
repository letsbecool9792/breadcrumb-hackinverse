package com.lbc.breadcrumb.ocr

import android.content.Context
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.ReadingDao
import com.lbc.breadcrumb.data.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkReadingTest {

    private lateinit var fakeDao: FakeReadingDao

    @Before
    fun setUp() {
        fakeDao = FakeReadingDao()
    }

    @Test
    fun existingMemoryTitleIsPreservedWhenPageHasTitle() = runBlocking {
        val memory = Memory(
            id = "m1",
            type = MemoryType.LINK,
            rawText = "https://example.com/article",
            title = "Existing Chrome Share Title",
            extractedText = null,
        )
        fakeDao.memories[memory.id] = memory

        val fakeReader = object : LinkReader {
            override suspend fun readLink(url: String): LinkReadResult =
                LinkReadResult(title = "Fetched Web Title", description = "Fetched web description")
        }

        var uploadCalls = 0
        val linkReading = LinkReading(
            readingDao = fakeDao,
            reader = fakeReader,
            scheduleUpload = { uploadCalls++ },
        )

        val processed = linkReading.processMemory(memory)
        assertTrue(processed)

        val after = fakeDao.getById("m1")!!
        // Existing title must be preserved
        assertEquals("Existing Chrome Share Title", after.title)
        assertEquals("Fetched web description", after.extractedText)
        assertEquals(SyncState.PENDING, after.syncState)
        assertEquals(1, uploadCalls)
    }

    @Test
    fun missingMemoryTitleIsPopulatedFromPageTitle() = runBlocking {
        val memory = Memory(
            id = "m2",
            type = MemoryType.LINK,
            rawText = "https://example.com/article",
            title = null,
            extractedText = null,
        )
        fakeDao.memories[memory.id] = memory

        val fakeReader = object : LinkReader {
            override suspend fun readLink(url: String): LinkReadResult =
                LinkReadResult(title = "Fetched Web Title", description = "Fetched description")
        }

        val linkReading = LinkReading(
            readingDao = fakeDao,
            reader = fakeReader,
            scheduleUpload = {},
        )

        val processed = linkReading.processMemory(memory)
        assertTrue(processed)

        val after = fakeDao.getById("m2")!!
        assertEquals("Fetched Web Title", after.title)
        assertEquals("Fetched description", after.extractedText)
        assertEquals(SyncState.PENDING, after.syncState)
    }

    @Test
    fun networkOrReadFailureBecomesFinalEmptyTextAndPreservesTitle() = runBlocking {
        val memory = Memory(
            id = "m3",
            type = MemoryType.LINK,
            rawText = "https://example.com/bad",
            title = "My Preserved Title",
            extractedText = null,
        )
        fakeDao.memories[memory.id] = memory

        val failingReader = object : LinkReader {
            override suspend fun readLink(url: String): LinkReadResult {
                throw java.io.IOException("Network down")
            }
        }

        var uploadCalls = 0
        val linkReading = LinkReading(
            readingDao = fakeDao,
            reader = failingReader,
            scheduleUpload = { uploadCalls++ },
        )

        val processed = linkReading.processMemory(memory)
        assertTrue(processed)

        val after = fakeDao.getById("m3")!!
        assertEquals("My Preserved Title", after.title)
        // Failure must result in empty string, not null, so it does not endlessly retry
        assertEquals("", after.extractedText)
        assertEquals(1, uploadCalls)
    }

    @Test
    fun fourPageBatchingProcessesFourPagesAtATime() = runBlocking {
        for (i in 1..10) {
            val memory = Memory(
                id = "link$i",
                type = MemoryType.LINK,
                rawText = "https://example.com/page$i",
                capturedAt = i * 1000L,
                extractedText = null,
            )
            fakeDao.memories[memory.id] = memory
        }

        assertEquals(10, fakeDao.countUnreadLinks())

        val fakeReader = object : LinkReader {
            override suspend fun readLink(url: String): LinkReadResult =
                LinkReadResult(title = "Title for $url", description = "Desc for $url")
        }

        val linkReading = LinkReading(
            readingDao = fakeDao,
            reader = fakeReader,
            scheduleUpload = {},
        )

        // Process first batch of 4
        val firstBatchCount = linkReading.readBatch(4)
        assertEquals(4, firstBatchCount)
        assertEquals(6, fakeDao.countUnreadLinks())

        // Process second batch of 4
        val secondBatchCount = linkReading.readBatch(4)
        assertEquals(4, secondBatchCount)
        assertEquals(2, fakeDao.countUnreadLinks())

        // Process remaining 2
        val lastBatchCount = linkReading.readBatch(4)
        assertEquals(2, lastBatchCount)
        assertEquals(0, fakeDao.countUnreadLinks())
    }

    @Test
    fun readAllPendingProcessesAllLinksUntilNoneRemain() = runBlocking {
        for (i in 1..9) {
            val memory = Memory(
                id = "link$i",
                type = MemoryType.LINK,
                rawText = "https://example.com/page$i",
                capturedAt = i * 1000L,
                extractedText = null,
            )
            fakeDao.memories[memory.id] = memory
        }

        val fakeReader = object : LinkReader {
            override suspend fun readLink(url: String): LinkReadResult =
                LinkReadResult(title = "Title for $url", description = "Desc")
        }

        val linkReading = LinkReading(
            readingDao = fakeDao,
            reader = fakeReader,
            scheduleUpload = {},
        )

        val total = linkReading.readAllPending(batchSize = 4)
        assertEquals(9, total)
        assertEquals(0, fakeDao.countUnreadLinks())
    }

    @Test
    fun memoryDeletedMidReadStaysDeleted() = runBlocking {
        val memory = Memory(
            id = "to_delete",
            type = MemoryType.LINK,
            rawText = "https://example.com/delete",
            extractedText = null,
        )
        fakeDao.memories[memory.id] = memory

        val fakeReader = object : LinkReader {
            override suspend fun readLink(url: String): LinkReadResult {
                // Delete row during read
                fakeDao.memories.remove("to_delete")
                return LinkReadResult(title = "Title", description = "Desc")
            }
        }

        val linkReading = LinkReading(
            readingDao = fakeDao,
            reader = fakeReader,
            scheduleUpload = {},
        )

        val processed = linkReading.processMemory(memory)
        assertFalse(processed)
        assertNull(fakeDao.getById("to_delete"))
    }

    private class FakeReadingDao : ReadingDao {
        val memories = mutableMapOf<String, Memory>()

        override fun observeUnreadImages(): Flow<List<Memory>> =
            flowOf(memories.values.filter { it.type == MemoryType.IMAGE && it.extractedText == null })

        override suspend fun getUnreadImages(): List<Memory> =
            memories.values.filter { it.type == MemoryType.IMAGE && it.extractedText == null }

        override suspend fun getNewestUnreadImages(limit: Int): List<Memory> =
            memories.values
                .filter { it.type == MemoryType.IMAGE && it.extractedText == null }
                .sortedByDescending { it.capturedAt }
                .take(limit)

        override suspend fun countUnreadImages(): Int =
            memories.values.count { it.type == MemoryType.IMAGE && it.extractedText == null }

        override fun observeUnreadMedia(): Flow<List<Memory>> =
            flowOf(memories.values.filter { (it.type == MemoryType.IMAGE || it.type == MemoryType.PDF) && it.extractedText == null })

        override suspend fun getNewestUnreadMedia(limit: Int): List<Memory> =
            memories.values
                .filter { (it.type == MemoryType.IMAGE || it.type == MemoryType.PDF) && it.extractedText == null }
                .sortedByDescending { it.capturedAt }
                .take(limit)

        override fun observeUnreadLinks(): Flow<List<Memory>> =
            flowOf(memories.values.filter { (it.type == MemoryType.LINK || (it.hasLink && it.type == MemoryType.TEXT)) && it.extractedText == null })

        override suspend fun getNewestUnreadLinks(limit: Int): List<Memory> =
            memories.values
                .filter { (it.type == MemoryType.LINK || (it.hasLink && it.type == MemoryType.TEXT)) && it.extractedText == null }
                .sortedByDescending { it.capturedAt }
                .take(limit)

        override suspend fun countUnreadLinks(): Int =
            memories.values.count { (it.type == MemoryType.LINK || (it.hasLink && it.type == MemoryType.TEXT)) && it.extractedText == null }

        override suspend fun getById(id: String): Memory? = memories[id]

        override suspend fun updateExtractedText(id: String, extractedText: String, updatedAt: Long): Int {
            val mem = memories[id] ?: return 0
            if (mem.extractedText != null) return 0
            memories[id] = mem.copy(
                extractedText = extractedText,
                updatedAt = updatedAt,
                syncState = if (extractedText.isNotEmpty()) SyncState.PENDING else mem.syncState,
            )
            return 1
        }

        override suspend fun updateLinkReading(
            id: String,
            extractedText: String,
            title: String?,
            updatedAt: Long,
        ): Int {
            val mem = memories[id] ?: return 0
            if (mem.extractedText != null) return 0
            val newTitle = if (mem.title.isNullOrBlank()) title else mem.title
            val shouldPending = extractedText.isNotEmpty() || (!title.isNullOrEmpty() && mem.title.isNullOrBlank())
            memories[id] = mem.copy(
                extractedText = extractedText,
                title = newTitle,
                updatedAt = updatedAt,
                syncState = if (shouldPending) SyncState.PENDING else mem.syncState,
            )
            return 1
        }

        override fun searchByMatch(matchQuery: String, limit: Int): Flow<List<Memory>> = flowOf(emptyList())

        override suspend fun searchByMatchOnce(matchQuery: String, limit: Int): List<Memory> = emptyList()
    }
}
