package com.lbc.breadcrumb.ocr

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryDao
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.data.ReadingDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OcrQueueTest {

    private lateinit var context: Context
    private lateinit var db: BreadcrumbDatabase
    private lateinit var memoryDao: MemoryDao
    private lateinit var readingDao: ReadingDao
    private lateinit var originalsDir: File
    private lateinit var originalStore: OriginalStore
    private lateinit var scope: CoroutineScope
    private val readingOriginals = MutableStateFlow(true)
    private var queueJob: Job? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        memoryDao = db.memoryDao()
        readingDao = db.readingDao()

        originalsDir = File(context.filesDir, "originals").apply { mkdirs() }
        originalStore = OriginalStore(context, originalsDir)
        scope = CoroutineScope(Dispatchers.IO)
    }

    @After
    fun tearDown() {
        queueJob?.cancel()
        scope.cancel()
        db.close()
        originalsDir.deleteRecursively()
    }

    @Test
    fun readingOriginalsGoesFalseWhenQueueStartsWithNoUnreadImages() = runBlocking {
        val fakeReader = FakeReader { "text" }

        val queue = OcrQueue(
            context = context,
            readingDao = readingDao,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = fakeReader,
        )

        assertTrue(readingOriginals.value)
        queueJob = queue.start()

        // Wait until queue looks and sees 0 unread images
        withTimeout(3000) {
            while (readingOriginals.value) {
                delay(20)
            }
        }
        assertFalse(readingOriginals.value)
    }

    @Test
    fun queueReadsUnreadImageWritesExtractedTextAndCallsUploadScheduler() = runBlocking {
        var uploadCalls = 0
        uploadScheduler = { uploadCalls++ }

        val imageFile = File(originalsDir, "img1.png").apply { writeText("fake image content") }
        val memory = Memory(
            id = "img1",
            type = MemoryType.IMAGE,
            localUri = Uri.fromFile(imageFile).toString(),
            extractedText = null,
        )
        memoryDao.upsert(memory)

        val fakeReader = FakeReader { "Recognized OCR Words" }

        val queue = OcrQueue(
            context = context,
            readingDao = readingDao,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = fakeReader,
        )

        queueJob = queue.start()

        withTimeout(3000) {
            while (readingDao.getById("img1")?.extractedText == null) {
                delay(20)
            }
        }

        val updated = readingDao.getById("img1")
        assertEquals("Recognized OCR Words", updated?.extractedText)
        assertEquals(1, uploadCalls)
        assertFalse(readingOriginals.value)
    }

    @Test
    fun missingOrUnreadableImageFileIsMarkedEmptyAndDoesNotBlockTheQueue() = runBlocking {
        var uploadCalls = 0
        uploadScheduler = { uploadCalls++ }

        val goodFile = File(originalsDir, "img_good.png").apply { writeText("good content") }
        memoryDao.upsertAll(
            listOf(
                Memory(id = "img_broken", type = MemoryType.IMAGE, localUri = null, capturedAt = 1000),
                Memory(id = "img_good", type = MemoryType.IMAGE, localUri = Uri.fromFile(goodFile).toString(), capturedAt = 2000),
            ),
        )

        val fakeReader = FakeReader { file -> if (file.name == "img_good.png") "Good text" else "" }

        val queue = OcrQueue(
            context = context,
            readingDao = readingDao,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = fakeReader,
        )

        queueJob = queue.start()

        withTimeout(3000) {
            while (readingDao.countUnreadImages() > 0) {
                delay(20)
            }
        }

        assertEquals("", readingDao.getById("img_broken")?.extractedText)
        assertEquals("Good text", readingDao.getById("img_good")?.extractedText)
        assertEquals(2, uploadCalls)
        assertFalse(readingOriginals.value)
    }

    @Test
    fun memoryDeletedMidReadStaysDeletedAndIsNotResurrected() = runBlocking {
        val imageFile = File(originalsDir, "to_delete.png").apply { writeText("content") }
        val memory = Memory(
            id = "to_delete",
            type = MemoryType.IMAGE,
            localUri = Uri.fromFile(imageFile).toString(),
        )
        memoryDao.upsert(memory)

        val fakeReader = FakeReader {
            runBlocking { memoryDao.deleteEverywhere(memory, now = 2000) }
            "text should not be saved"
        }

        val queue = OcrQueue(
            context = context,
            readingDao = readingDao,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = fakeReader,
        )

        queueJob = queue.start()

        delay(300)
        assertNull(readingDao.getById("to_delete"))
        assertNull(memoryDao.getById("to_delete"))
        assertEquals(1, memoryDao.pendingDeleteCount())
    }

    private class FakeReader(
        private val readerAction: suspend (File) -> String,
    ) : ImageTextReader {
        override suspend fun readText(file: File): String = readerAction(file)
    }
}
