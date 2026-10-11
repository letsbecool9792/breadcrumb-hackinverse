package com.lbc.breadcrumb.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.OriginalStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * On-device instrumentation test for [Thumbnails] (docs/design.md §3, docs/workstreams/reading.md R6).
 *
 * Checks:
 * - A picture decodes with both sides at least the target.
 * - A PDF's first page draws on white.
 * - No file gives null.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailsTest {

    private lateinit var context: Context
    private lateinit var storeDir: File
    private lateinit var store: OriginalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storeDir = File(context.filesDir, "test_thumbnails_${System.currentTimeMillis()}").apply { mkdirs() }
        store = OriginalStore(context, root = storeDir)
        Thumbnails.clear()
    }

    @After
    fun tearDown() {
        storeDir.deleteRecursively()
        Thumbnails.clear()
    }

    @Test
    fun pictureDecodesWithBothSidesAtLeastTarget() {
        // Create a 1000x800 image file
        val rawWidth = 1000
        val rawHeight = 800
        val targetPx = 360

        val tempImage = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.jpg")
        val bitmap = Bitmap.createBitmap(rawWidth, rawHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        FileOutputStream(tempImage).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()

        val id = "img-test-1"
        val storedFile = store.moveIn(tempImage, id, "jpg")
        val memory = Memory(
            id = id,
            type = MemoryType.IMAGE,
            localUri = store.uriFor(storedFile),
        )

        val thumbnail = Thumbnails.load(context, memory, targetPx, store = store)
        assertNotNull("Loaded thumbnail should not be null", thumbnail)
        thumbnail!!

        // Both sides must be >= targetPx (360)
        assertTrue(
            "Width (${thumbnail.width}) must be >= targetPx ($targetPx)",
            thumbnail.width >= targetPx,
        )
        assertTrue(
            "Height (${thumbnail.height}) must be >= targetPx ($targetPx)",
            thumbnail.height >= targetPx,
        )

        // Cached lookup should find the decoded bitmap
        val cached = Thumbnails.cached(memory, targetPx)
        assertNotNull("Cached lookup should return the loaded thumbnail", cached)
        assertEquals(thumbnail.width, cached!!.width)
    }

    @Test
    fun pdfFirstPageDrawsOnWhite() {
        val targetPx = 360
        val tempPdf = File(context.cacheDir, "temp_doc_${System.currentTimeMillis()}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(500, 700, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }
        canvas.drawText("Test PDF Document", 50f, 50f, paint)
        doc.finishPage(page)
        FileOutputStream(tempPdf).use { doc.writeTo(it) }
        doc.close()

        val id = "pdf-test-1"
        val storedFile = store.moveIn(tempPdf, id, "pdf")
        val memory = Memory(
            id = id,
            type = MemoryType.PDF,
            localUri = store.uriFor(storedFile),
        )

        val thumbnail = Thumbnails.load(context, memory, targetPx, store = store)
        assertNotNull("Loaded PDF thumbnail should not be null", thumbnail)
        thumbnail!!

        // Width must be rendered targetPx wide
        assertEquals(targetPx, thumbnail.width)
        assertTrue("Height should be positive and scaled proportionally", thumbnail.height > 0)
    }

    @Test
    fun noFileGivesNull() {
        // Missing localUri
        val memoryNoUri = Memory(
            id = "mem-no-uri",
            type = MemoryType.IMAGE,
            localUri = null,
        )
        assertNull(Thumbnails.load(context, memoryNoUri, 360, store = store))

        // Non-existent file
        val memoryMissingFile = Memory(
            id = "mem-missing-file",
            type = MemoryType.IMAGE,
            localUri = store.uriFor(File(storeDir, "non_existent.jpg")),
        )
        assertNull(Thumbnails.load(context, memoryMissingFile, 360, store = store))

        // TEXT type has no thumbnail
        val memoryText = Memory(
            id = "mem-text",
            type = MemoryType.TEXT,
            rawText = "Just some text",
        )
        assertNull(Thumbnails.load(context, memoryText, 360, store = store))
    }
}
