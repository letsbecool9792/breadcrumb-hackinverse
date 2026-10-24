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
import kotlinx.coroutines.flow.first
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

        // Never filesDir/originals: the tests run in the app's own process, so that
        // is the phone's real originals, and tearDown deletes this folder.
        originalsDir = File(context.cacheDir, "ocr_queue_test_${System.currentTimeMillis()}").apply { mkdirs() }
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
            scheduleWork = { uploadCalls++ },
        )

        queueJob = queue.start()

        withTimeout(3000) {
            while (readingDao.getById("img1")?.extractedText == null) {
                delay(20)
            }
        }

        // The queue calls the upload hook and says it is done just after the
        // write waited for above: wait for that too, or the asserts race it.
        withTimeout(3000) { readingOriginals.first { !it } }

        val updated = readingDao.getById("img1")
        assertEquals("Recognized OCR Words", updated?.extractedText)
        assertEquals(1, uploadCalls)
        assertFalse(readingOriginals.value)
    }

    @Test
    fun queueReadsUnreadPdfWritesExtractedTextAndCallsUploadScheduler() = runBlocking {
        var uploadCalls = 0

        val pdfFile = File(originalsDir, "doc1.pdf").apply { writeText("fake pdf content") }
        val memory = Memory(
            id = "doc1",
            type = MemoryType.PDF,
            localUri = Uri.fromFile(pdfFile).toString(),
            extractedText = null,
        )
        memoryDao.upsert(memory)

        val fakeImageReader = FakeReader { "" }
        val fakePdfReader = object : PdfTextReader {
            override suspend fun readText(file: File): String = "Extracted PDF Contract Text"
        }

        val queue = OcrQueue(
            context = context,
            readingDao = readingDao,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = fakeImageReader,
            pdfReader = fakePdfReader,
            scheduleWork = { uploadCalls++ },
        )

        queueJob = queue.start()

        withTimeout(3000) {
            while (readingDao.getById("doc1")?.extractedText == null) {
                delay(20)
            }
        }
        withTimeout(3000) { readingOriginals.first { !it } }

        val updated = readingDao.getById("doc1")
        assertEquals("Extracted PDF Contract Text", updated?.extractedText)
        assertEquals(1, uploadCalls)
        assertFalse(readingOriginals.value)
    }

    @Test
    fun missingOrUnreadableImageFileIsMarkedEmptyAndDoesNotBlockTheQueue() = runBlocking {
        var uploadCalls = 0

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
            scheduleWork = { uploadCalls++ },
        )

        queueJob = queue.start()

        withTimeout(3000) {
            while (readingDao.countUnreadImages() > 0) {
                delay(20)
            }
        }
        withTimeout(3000) { readingOriginals.first { !it } }

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
            runBlocking { memoryDao.delete(memory) }
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
    }

    @Test
    fun aPictureSavedDuringALargeImportIsReadAfterTheCurrentBatch() = runBlocking {
        val order = mutableListOf<String>()
        memoryDao.upsertAll((1..25).map { image("old$it", capturedAt = it * 1000L) })

        val fakeReader = FakeReader { file ->
            val id = file.nameWithoutExtension
            val first = synchronized(order) { order += id; order.size == 1 }
            // A screenshot is shared while the queue reads the first of the import.
            if (first) memoryDao.upsert(image("new", capturedAt = 99_000L))
            "text"
        }

        queueJob = OcrQueue(
            context = context,
            readingDao = readingDao,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = fakeReader,
            scheduleWork = {},
        ).start()

        withTimeout(5000) {
            while (synchronized(order) { order.size } < 26) {
                delay(20)
            }
        }

        // Right after the batch it arrived in, not after the other 15.
        assertEquals(OcrQueue.BATCH, synchronized(order) { order.indexOf("new") })
    }

    @Test
    fun aPassWithAFailedReadEndsWithReadingOriginalsFalse() = runBlocking {
        memoryDao.upsertAll(listOf(image("good1", capturedAt = 1000L), image("bad", capturedAt = 2000L), image("good2", capturedAt = 3000L)))
        // The database refuses one memory's write, so its row stays unread.
        val failing = object : ReadingDao by readingDao {
            override suspend fun updateExtractedText(id: String, extractedText: String, updatedAt: Long): Int =
                if (id == "bad") throw IllegalStateException("disk full") else readingDao.updateExtractedText(id, extractedText, updatedAt)
        }

        queueJob = OcrQueue(
            context = context,
            readingDao = failing,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = FakeReader { "text" },
            scheduleWork = {},
        ).start()

        withTimeout(3000) {
            while (readingDao.countUnreadImages() > 1) {
                delay(20)
            }
        }
        // The flag means a pass is running, not that something is unread: one
        // failed read must not hold every unread picture back from upload.
        withTimeout(3000) { readingOriginals.first { !it } }

        assertEquals("text", readingDao.getById("good1")?.extractedText)
        assertEquals("text", readingDao.getById("good2")?.extractedText)
        assertNull(readingDao.getById("bad")?.extractedText)
    }

    @Test
    fun oneBadRowDoesNotEndTheQueue() = runBlocking {
        memoryDao.upsertAll((1..3).map { image("p$it", capturedAt = it * 1000L) })
        var calls = 0

        queueJob = OcrQueue(
            context = context,
            readingDao = readingDao,
            originals = originalStore,
            scope = scope,
            readingOriginals = readingOriginals,
            reader = FakeReader { "text" },
            // The first picture's read fails after its write; the queue must go on.
            scheduleWork = { if (calls++ == 0) throw IllegalStateException("upload hook failed") },
        ).start()

        withTimeout(3000) {
            while (readingDao.countUnreadImages() > 0) {
                delay(20)
            }
        }
        withTimeout(3000) { readingOriginals.first { !it } }

        assertEquals(0, readingDao.countUnreadImages())
    }

    private fun image(id: String, capturedAt: Long): Memory {
        val file = File(originalsDir, "$id.png").apply { writeText("picture $id") }
        return Memory(id = id, type = MemoryType.IMAGE, localUri = Uri.fromFile(file).toString(), capturedAt = capturedAt)
    }

    private class FakeReader(
        private val readerAction: suspend (File) -> String,
    ) : ImageTextReader {
        override suspend fun readText(file: File): String = readerAction(file)
    }
}
