package com.lbc.breadcrumb.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
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
class MlKitImageTextReaderTest {

    private lateinit var context: Context
    private lateinit var reader: MlKitImageTextReader
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        reader = MlKitImageTextReader(context)
        testDir = File(context.cacheDir, "ocr_test_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        reader.close()
        testDir.deleteRecursively()
    }

    @Test
    fun recognizesTextFromRenderedImageOffline() = runBlocking {
        val file = File(testDir, "sample_text.png")
        createImageWithText(file, "BREADCRUMB TEST 123")

        val result = reader.readText(file)
        assertTrue("Expected recognized text to contain BREADCRUMB, got: '$result'", result.contains("BREADCRUMB"))
        assertTrue("Expected recognized text to contain 123, got: '$result'", result.contains("123"))
    }

    @Test
    fun returnsEmptyStringForBlankImage() = runBlocking {
        val file = File(testDir, "blank.png")
        createBlankImage(file)

        val result = reader.readText(file)
        assertEquals("", result)
    }

    @Test
    fun returnsEmptyStringForMissingFileWithoutCrashing() = runBlocking {
        val file = File(testDir, "non_existent.jpg")
        val result = reader.readText(file)
        assertEquals("", result)
    }

    @Test
    fun returnsEmptyStringForCorruptedZeroByteFileWithoutCrashing() = runBlocking {
        val file = File(testDir, "empty.jpg").apply { createNewFile() }
        val result = reader.readText(file)
        assertEquals("", result)
    }

    @Test
    fun honoursExifRotationOnJpegImage() = runBlocking {
        val file = File(testDir, "exif_normal.jpg")
        createImageWithText(file, "ROTATION OK", format = Bitmap.CompressFormat.JPEG)

        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()

        val result = reader.readText(file)
        assertTrue("Expected ROTATION OK, got: '$result'", result.contains("ROTATION"))
    }

    private fun createImageWithText(
        file: File,
        text: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    ) {
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 36f
            isAntiAlias = true
        }
        canvas.drawText(text, 30f, 110f, paint)

        FileOutputStream(file).use { out ->
            bitmap.compress(format, 95, out)
        }
        bitmap.recycle()
    }

    private fun createBlankImage(file: File) {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()
    }
}
