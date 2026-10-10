package com.lbc.breadcrumb.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR using ML Kit's bundled model (Latin script only).
 *
 * Uses the bundled model (`com.google.mlkit:text-recognition`), not the
 * play-services one: works completely offline from the first save without
 * waiting for a model download.
 *
 * Downsamples big images first to avoid out-of-memory crashes on high-res
 * camera photos and screenshots, and honours EXIF orientation so rotated photos
 * are read right-side up.
 */
class MlKitImageTextReader(
    private val context: Context,
) : ImageTextReader, Closeable {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun readText(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return@withContext ""
        }

        // 1. Check EXIF orientation
        val orientation = try {
            val exif = ExifInterface(file.absolutePath)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        // 2. Read dimensions without allocating pixel memory
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) {
            return@withContext ""
        }

        // 3. Downsample big images to fit within MAX_DIMENSION (avoids OOM on 48MP+ photos)
        var sampleSize = 1
        val maxDimension = maxOf(origWidth, origHeight)
        while ((maxDimension / sampleSize) > MAX_DIMENSION) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return@withContext ""

        // 4. Run ML Kit text recognition
        try {
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
            val result = recognizer.process(inputImage).await()
            result.text.trim()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            "" // Unreadable image returned as empty so it never blocks
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        recognizer.close()
    }

    companion object {
        /**
         * Longest dimension in pixels before downsampling.
         * 2048 preserves sharp text in high-res screenshots while bounding memory.
         */
        private const val MAX_DIMENSION = 2048
    }
}

/**
 * Awaits completion of a Google Play / ML Kit [Task] in a coroutine.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
