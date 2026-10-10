package com.lbc.breadcrumb.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AndroidPdfTextReaderTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "pdf_test_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun returnsEmptyStringForNonExistentOrEmptyFile() = runBlocking {
        val reader = AndroidPdfTextReader(context)

        val nonExistent = File(testDir, "missing.pdf")
        assertEquals("", reader.readText(nonExistent))

        val emptyFile = File(testDir, "empty.pdf").apply { createNewFile() }
        assertEquals("", reader.readText(emptyFile))
    }

    @Test
    fun returnsEmptyStringForCorruptedFileWithoutCrashing() = runBlocking {
        val reader = AndroidPdfTextReader(context)
        val corruptFile = File(testDir, "corrupt.pdf").apply {
            writeText("not a valid pdf header")
        }
        assertEquals("", reader.readText(corruptFile))
    }

    @Test
    fun readsPdfWithTextUsingRealPdfDocument() = runBlocking {
        val pdfFile = File(testDir, "sample_doc.pdf")
        createPdfWithText(pdfFile, "Breadcrumb PDF Intern Report 2026")

        val fakeImageReader = object : ImageTextReader {
            override suspend fun readText(file: File): String = ""
            override suspend fun readText(bitmap: Bitmap): String = "Breadcrumb PDF Intern Report 2026"
        }

        val reader = AndroidPdfTextReader(context, imageTextReader = fakeImageReader)
        val text = reader.readText(pdfFile)

        assertTrue(
            "Expected text to contain Breadcrumb PDF Intern Report, got: '$text'",
            text.contains("Breadcrumb PDF Intern Report 2026") || text.contains("Breadcrumb"),
        )
    }

    @Test
    fun ocrsScannedPdfWhenNoTextLayerExists() = runBlocking {
        val pdfFile = File(testDir, "scanned_doc.pdf")
        createScannedPdfWithBitmap(pdfFile, "Scanned Image Text Here")

        var bitmapOcrCalled = false
        val fakeImageReader = object : ImageTextReader {
            override suspend fun readText(file: File): String = ""
            override suspend fun readText(bitmap: Bitmap): String {
                bitmapOcrCalled = true
                return "Scanned Image Text Here"
            }
        }

        val reader = AndroidPdfTextReader(context, imageTextReader = fakeImageReader)
        val text = reader.readText(pdfFile)

        assertTrue("Expected OCR to be called on bitmap", bitmapOcrCalled)
        assertEquals("Scanned Image Text Here", text)
    }

    @Test
    fun capsOcrToFirstSixPagesOnMultiPagePdf() = runBlocking {
        val pdfFile = File(testDir, "ten_pages.pdf")
        createMultiPageScannedPdf(pdfFile, pageCount = 10)

        var ocrPageCount = 0
        val fakeImageReader = object : ImageTextReader {
            override suspend fun readText(file: File): String = ""
            override suspend fun readText(bitmap: Bitmap): String {
                ocrPageCount++
                return "Page $ocrPageCount content"
            }
        }

        val reader = AndroidPdfTextReader(context, imageTextReader = fakeImageReader)
        val text = reader.readText(pdfFile)

        assertEquals("OCR must cap at first 6 pages", 6, ocrPageCount)
        assertTrue(text.contains("Page 1"))
        assertTrue(text.contains("Page 6"))
    }

    private fun createPdfWithText(file: File, text: String) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(600, 800, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText(text, 50f, 100f, paint)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun createScannedPdfWithBitmap(file: File, textOnBitmap: String) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(600, 800, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // Create an image containing text to simulate a scanned document without text layer
        val bitmap = Bitmap.createBitmap(500, 200, Bitmap.Config.ARGB_8888)
        val bCanvas = Canvas(bitmap)
        bCanvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }
        bCanvas.drawText(textOnBitmap, 20f, 80f, paint)

        canvas.drawBitmap(bitmap, 50f, 50f, null)
        bitmap.recycle()

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun createMultiPageScannedPdf(file: File, pageCount: Int) {
        val doc = PdfDocument()
        for (i in 1..pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(600, 800, i).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val bitmap = Bitmap.createBitmap(400, 150, Bitmap.Config.ARGB_8888)
            val bCanvas = Canvas(bitmap)
            bCanvas.drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
            }
            bCanvas.drawText("Scanned page $i", 20f, 60f, paint)

            canvas.drawBitmap(bitmap, 50f, 50f, null)
            bitmap.recycle()

            doc.finishPage(page)
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}
