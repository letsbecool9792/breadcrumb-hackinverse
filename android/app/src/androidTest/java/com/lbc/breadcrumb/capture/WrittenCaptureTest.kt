package com.lbc.breadcrumb.capture

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ocr.workScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keep, on the phone. These tests run in the app's own process, so they write to
 * the person's real database: they look only at what they create, and remove
 * only that. They must never clear or list-and-delete everything -- that wiped
 * saved memories once. Uploads are held off meanwhile, so the test's words never
 * reach the server.
 */
@RunWith(AndroidJUnit4::class)
class WrittenCaptureTest {

    private lateinit var app: BreadcrumbApp
    private val createdUris = mutableListOf<Uri>()
    private var before = emptySet<String>()
    private var scheduler: (Context) -> Unit = {}

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext<Context>() as BreadcrumbApp
        scheduler = workScheduler
        workScheduler = {}
        before = runBlocking { app.database.memoryDao().getAll().map { it.id }.toSet() }
    }

    @After
    fun tearDown() {
        workScheduler = scheduler
        for (uri in createdUris) {
            try {
                app.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
        runBlocking {
            // Only what this test made.
            created().forEach {
                app.database.memoryDao().deleteEverywhere(it, System.currentTimeMillis())
                app.originals.delete(it)
            }
        }
    }

    /** The memories made since the test began, oldest first. */
    private suspend fun created(): List<Memory> =
        app.database.memoryDao().getAll().filter { it.id !in before }.sortedBy { it.capturedAt }

    @Test
    fun textAloneGivesOneMemoryByTheShareRulesWithNoSource() = runBlocking {
        // Plain text -> TEXT type, hasLink false, no source, no note
        val textCount = Keep.keep(app, text = "Remember to buy milk", uris = emptyList())
        assertEquals(1, textCount)

        val textMemories = created()
        assertEquals(1, textMemories.size)
        val textMem = textMemories.first()
        assertEquals(MemoryType.TEXT, textMem.type)
        assertFalse(textMem.hasLink)
        assertEquals("Remember to buy milk", textMem.rawText)
        assertNull(textMem.sourceApp)
        assertNull(textMem.sourceAppLabel)
        assertNull(textMem.note)

        // Text containing a URL -> LINK type, hasLink true, no source
        val linkCount = Keep.keep(app, text = "Interesting article https://example.com/story", uris = emptyList())
        assertEquals(1, linkCount)

        val allMemories = created()
        assertEquals(2, allMemories.size)
        val linkMem = allMemories.first { it.id != textMem.id }
        assertEquals(MemoryType.LINK, linkMem.type)
        assertTrue(linkMem.hasLink)
        assertEquals("Interesting article https://example.com/story", linkMem.rawText)
        assertNull(linkMem.sourceApp)
        assertNull(linkMem.sourceAppLabel)
        assertNull(linkMem.note)
    }

    @Test
    fun filesGiveOneMemoryEachWithTheTextAsTheirNote() = runBlocking {
        val uri1 = createMediaUri("photo1.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val uri2 = createMediaUri("photo2.jpg", "image/jpeg", byteArrayOf(4, 5, 6))

        val noteText = "Two snapshots from the meetup"
        val keptCount = Keep.keep(app, text = noteText, uris = listOf(uri1, uri2))
        assertEquals(2, keptCount)

        val memories = created()
        assertEquals(2, memories.size)

        for (memory in memories) {
            assertEquals(MemoryType.IMAGE, memory.type)
            assertEquals(noteText, memory.note)
            assertNull(memory.rawText)
            assertNull(memory.sourceApp)
            assertNull(memory.sourceAppLabel)
            assertNotNull(memory.localUri)

            val stored = app.originals.fileFor(memory)
            assertNotNull(stored)
            assertTrue(stored!!.exists())
        }
    }

    @Test
    fun emptyKeepSavesNothingAndReportsZero() = runBlocking {
        val count = Keep.keep(app, text = "", uris = emptyList())
        assertEquals(0, count)
        assertEquals(0, created().size)
    }

    private fun createMediaUri(name: String, mime: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
        }
        val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to insert media $name")
        createdUris += uri
        app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        return uri
    }
}
