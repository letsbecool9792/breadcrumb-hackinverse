package com.lbc.breadcrumb.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pictures as they go to the server, decoded and re-encoded on the device.
 */
@RunWith(AndroidJUnit4::class)
class PicturesTest {

    private val dir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "pictures-test").apply { mkdirs() }

    @After
    fun clean() {
        dir.deleteRecursively()
    }

    private fun png(name: String, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return File(dir, name).apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun sizeOf(jpeg: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun aBigPictureGoesAsAJpegWithA1500PxLongSide() {
        assertEquals(1500 to 1000, sizeOf(Pictures.jpeg(png("big.png", 4500, 3000))!!))
    }

    @Test
    fun aSmallPictureKeepsItsSize() {
        assertEquals(800 to 600, sizeOf(Pictures.jpeg(png("small.png", 800, 600))!!))
    }

    @Test
    fun aFileThatIsNotAPictureIsNothingToSend() {
        val notAPicture = File(dir, "notes.jpg").apply { writeText("not a picture") }

        assertNull(Pictures.jpeg(notAPicture))
    }
}
