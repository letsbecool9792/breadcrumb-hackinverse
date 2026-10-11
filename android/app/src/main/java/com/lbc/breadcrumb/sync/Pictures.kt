package com.lbc.breadcrumb.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * A saved picture as it goes to the server for reading: a JPEG whose long
 * side is at most [LONG_SIDE], upright. Made in memory and sent; never
 * written anywhere (architecture rule 1).
 */
internal object Pictures {

    const val LONG_SIDE = 1_500
    private const val QUALITY = 85

    /** [file] as a JPEG to send, or null when it is not a picture the phone can decode. */
    fun jpeg(file: File): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeToFit(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null

        val (width, height) = sizeFor(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postScale(width / decoded.width.toFloat(), height / decoded.height.toFloat())
            postRotate(rotationOf(file).toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return try {
            ByteArrayOutputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                out.toByteArray()
            }
        } finally {
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
        }
    }

    /**
     * The largest power of two to decode a [width] x [height] picture at that
     * still leaves its long side at least [longSide], so the exact scale
     * after it only ever shrinks.
     */
    fun sampleSizeToFit(width: Int, height: Int, longSide: Int = LONG_SIDE): Int {
        var sampleSize = 1
        while (maxOf(width, height) / (sampleSize * 2) >= longSide) sampleSize *= 2
        return sampleSize
    }

    /** The size a [width] x [height] picture is sent at: the long side at most [longSide], the shape kept. */
    fun sizeFor(width: Int, height: Int, longSide: Int = LONG_SIDE): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= longSide) return width to height
        val scale = longSide / long.toDouble()
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun rotationOf(file: File): Int = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }
}
