package com.lbc.breadcrumb.imports

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.ImportDao
import com.lbc.breadcrumb.data.ImportedSource
import com.lbc.breadcrumb.data.MemoryDao
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.data.ReadingDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BulkImporterTest {

    private lateinit var context: Context
    private lateinit var db: BreadcrumbDatabase
    private lateinit var memoryDao: MemoryDao
    private lateinit var importDao: ImportDao
    private lateinit var readingDao: ReadingDao
    private lateinit var testOriginalsDir: File
    private lateinit var testOriginals: OriginalStore
    private lateinit var readingOriginals: MutableStateFlow<Boolean>
    private lateinit var scope: CoroutineScope
    private lateinit var importer: BulkImporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        memoryDao = db.memoryDao()
        importDao = db.importDao()
        readingDao = db.readingDao()

        testOriginalsDir = File(context.cacheDir, "test_originals_${UUID.randomUUID()}").apply { mkdirs() }
        testOriginals = OriginalStore(context, testOriginalsDir)
        readingOriginals = MutableStateFlow(false)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        importer = BulkImporter(
            context = context,
            database = db,
            memoryDao = memoryDao,
            importDao = importDao,
            readingDao = readingDao,
            originals = testOriginals,
            readingOriginals = readingOriginals,
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        db.close()
        testOriginalsDir.deleteRecursively()
    }

    @Test
    fun bookmarkImportCreatesOneMemoryPerBookmarkWithLinkTypeAndAddDate() = runBlocking {
        val htmlContent = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p>
                <DT><A HREF="https://qualcomm.com/jobs" ADD_DATE="1711929600">Qualcomm Jobs</A>
                <DT><A HREF="https://android.com" ADD_DATE="1714521600">Android Official</A>
            </DL><p>
        """.trimIndent()

        val bookmarkFile = File(context.cacheDir, "bookmarks_${UUID.randomUUID()}.html").apply {
            writeText(htmlContent)
        }

        try {
            importer.importBookmarks(Uri.fromFile(bookmarkFile)).join()

            val memories = memoryDao.getAll()
            assertEquals(2, memories.size)

            val qualcomm = memories.first { it.rawText == "https://qualcomm.com/jobs" }
            assertEquals(MemoryType.LINK, qualcomm.type)
            assertTrue(qualcomm.hasLink)
            assertEquals("Bookmarks", qualcomm.sourceAppLabel)
            assertEquals("Qualcomm Jobs", qualcomm.title)
            assertEquals(1711929600000L, qualcomm.contentCreatedAt)
            assertNull(qualcomm.extractedText)

            assertTrue(importDao.exists("bookmark:https://qualcomm.com/jobs"))
            assertTrue(importDao.exists("bookmark:https://android.com"))
        } finally {
            bookmarkFile.delete()
        }
    }

    @Test
    fun reimportingBookmarksSkipsDuplicates() = runBlocking {
        val htmlContent = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="https://example.com" ADD_DATE="1700000000">Example Site</A>
            </DL><p>
        """.trimIndent()

        val bookmarkFile = File(context.cacheDir, "bookmarks_${UUID.randomUUID()}.html").apply {
            writeText(htmlContent)
        }

        try {
            importer.importBookmarks(Uri.fromFile(bookmarkFile)).join()
            assertEquals(1, memoryDao.count())

            // Second import of the same bookmark
            importer.importBookmarks(Uri.fromFile(bookmarkFile)).join()
            assertEquals(1, memoryDao.count())
        } finally {
            bookmarkFile.delete()
        }
    }

    @Test
    fun deletedBookmarkIsNotRecreatedOnReimport() = runBlocking {
        val htmlContent = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="https://deleted.com" ADD_DATE="1700000000">To Delete</A>
            </DL><p>
        """.trimIndent()

        val bookmarkFile = File(context.cacheDir, "bookmarks_${UUID.randomUUID()}.html").apply {
            writeText(htmlContent)
        }

        try {
            importer.importBookmarks(Uri.fromFile(bookmarkFile)).join()
            val memory = memoryDao.getAll().first()
            assertEquals("https://deleted.com", memory.rawText)

            // User deletes the memory
            memoryDao.deleteEverywhere(memory, now = System.currentTimeMillis())
            assertEquals(0, memoryDao.count())

            // Re-importing must NOT resurrect the deleted memory
            importer.importBookmarks(Uri.fromFile(bookmarkFile)).join()
            assertEquals(0, memoryDao.count())
        } finally {
            bookmarkFile.delete()
        }
    }

    @Test
    fun filenameAndSizeDeduplicationLevelOneBeforeCopying() = runBlocking {
        // Pre-record key in imported_sources as if previously imported
        importDao.record(ImportedSource("file:Screenshot_20240315-103045.png:4096", 1_000L))

        val dummyFile = File(context.cacheDir, "Screenshot_20240315-103045.png").apply {
            writeBytes(ByteArray(4096))
        }

        try {
            importer.importVisualMedia(listOf(Uri.fromFile(dummyFile))).join()

            // Memory must NOT be created because filename + size matched in imported_sources
            assertEquals(0, memoryDao.count())
        } finally {
            dummyFile.delete()
        }
    }

    @Test
    fun sha256DeduplicationCatchesScreenshotSharedUnderDifferentName() = runBlocking {
        val imageBytes = "unique image bytes for testing sha256 duplicate detection".toByteArray(Charsets.UTF_8)

        // Simulate a screenshot that was already saved via share sheet into OriginalStore
        val existingId = UUID.randomUUID().toString()
        val existingOriginal = File(testOriginalsDir, "$existingId.jpg").apply {
            writeBytes(imageBytes)
        }

        // Now user imports a file with a completely DIFFERENT filename but identical bytes
        val candidateFile = File(context.cacheDir, "Imported_Renamed_Screenshot.png").apply {
            writeBytes(imageBytes)
        }

        try {
            importer.importVisualMedia(listOf(Uri.fromFile(candidateFile))).join()

            // No duplicate memory created
            assertEquals(0, memoryDao.count())

            // SHA-256 key recorded in imported_sources
            val expectedHash = Sha256.digest(existingOriginal)
            assertTrue(importDao.exists("sha256:$expectedHash"))
        } finally {
            candidateFile.delete()
        }
    }

    @Test
    fun anExifDateComesBeforeTheFileNameDate() = runBlocking {
        // Named for 1 January 2020, but the camera says 1 June 2023.
        val photo = File(context.cacheDir, "IMG_20200101_000000.jpg")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(photo.absolutePath).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2023:06:01 12:00:00")
            saveAttributes()
        }

        try {
            importer.importVisualMedia(listOf(Uri.fromFile(photo))).join()

            val imported = memoryDao.getAll().single()
            assertEquals(FileMetadata.parseExifDateString("2023:06:01 12:00:00"), imported.contentCreatedAt)
        } finally {
            photo.delete()
        }
    }

    @Test
    fun deletedFileMemoryIsNotRecreatedOnNextImport() = runBlocking {
        val imageBytes = "image content to test deleted-after-import behavior".toByteArray(Charsets.UTF_8)
        val imageFile = File(context.cacheDir, "Screenshot_20240315_120000.png").apply {
            writeBytes(imageBytes)
        }

        try {
            importer.importVisualMedia(listOf(Uri.fromFile(imageFile))).join()
            assertEquals(1, memoryDao.count())

            val memory = memoryDao.getAll().first()
            assertEquals(MemoryType.IMAGE, memory.type)

            // Delete everywhere and remove original
            memoryDao.deleteEverywhere(memory, now = System.currentTimeMillis())
            testOriginals.delete(memory)
            assertEquals(0, memoryDao.count())

            // Re-import must NOT resurrect the memory
            importer.importVisualMedia(listOf(Uri.fromFile(imageFile))).join()
            assertEquals(0, memoryDao.count())
        } finally {
            imageFile.delete()
        }
    }
}
