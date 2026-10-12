package com.lbc.breadcrumb.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.OriginalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * In-memory thumbnail caching and decoding for pictures and PDFs (docs/design.md §3).
 *
 * Sized at an eighth of the heap in KB. Decodes off the main thread with no external
 * image loading library.
 */
object Thumbnails {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxOf(1024, maxMemoryKb / 8)

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return maxOf(1, (value.width * value.height * 4) / 1024)
        }
    }

    /** Where thumbnails are decoded: at most [DECODES_AT_ONCE] at a time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val decoding = Dispatchers.IO.limitedParallelism(DECODES_AT_ONCE)
    private const val DECODES_AT_ONCE = 3

    /** Cache key format: "$id@$targetPx:$localUri" */
    fun key(memory: Memory, targetPx: Int): String = "${memory.id}@$targetPx:${memory.localUri}"

    /**
     * Looks up a cached thumbnail for [memory] at [targetPx].
     *
     * Falls back to smaller sizes already cached (360 or 140) so a picture flying
     * into a sheet or transitioning is never an empty box (design §3).
     */
    fun cached(memory: Memory, targetPx: Int): ImageBitmap? {
        val exact = cache.get(key(memory, targetPx))
        if (exact != null) return exact

        val fallbacks = when (targetPx) {
            1080, 900 -> listOf(360, 140)
            360 -> listOf(140, 900, 1080)
            140 -> listOf(360, 900, 1080)
            else -> listOf(360, 140)
        }
        for (fb in fallbacks) {
            val hit = cache.get(key(memory, fb))
            if (hit != null) return hit
        }
        return null
    }

    /** Returns only the exact cached thumbnail for [memory] at [targetPx], or null. */
    fun cachedExact(memory: Memory, targetPx: Int): ImageBitmap? = cache.get(key(memory, targetPx))

    /**
     * Computes the largest power-of-two inSampleSize that keeps both sides >= [targetPx].
     */
    internal fun sampleSize(rawWidth: Int, rawHeight: Int, targetPx: Int): Int {
        var sample = 1
        while (rawWidth / (sample * 2) >= targetPx && rawHeight / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return sample
    }

    /**
     * Loads a thumbnail for [memory] at [targetPx] off the main thread.
     *
     * Resolves the stored original through [OriginalStore.fileFor].
     * A picture is decoded with the largest power-of-two inSampleSize that keeps both sides >= targetPx.
     * A PDF's page one is rendered targetPx wide on white (eraseColor(WHITE), RENDER_MODE_FOR_DISPLAY).
     * Returns null when there is no file or it cannot be drawn.
     */
    fun load(
        context: Context,
        memory: Memory,
        targetPx: Int,
        store: OriginalStore = (context.applicationContext as? BreadcrumbApp)?.originals ?: OriginalStore(context),
    ): ImageBitmap? {
        if (targetPx <= 0) return null
        val cachedExact = cache.get(key(memory, targetPx))
        if (cachedExact != null) return cachedExact

        val file = store.fileFor(memory) ?: return null
        if (!file.exists() || !file.isFile) return null

        val bitmap = when (memory.type) {
            // A link's preview picture (og:image, or a video's thumbnail).
            MemoryType.IMAGE, MemoryType.LINK -> decodePicture(file, targetPx)
            MemoryType.PDF -> renderPdfPage(file, targetPx)
            else -> null
        } ?: return null

        val imageBitmap = bitmap.asImageBitmap()
        cache.put(key(memory, targetPx), imageBitmap)
        return imageBitmap
    }

    private fun decodePicture(file: File, targetPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val rawWidth = bounds.outWidth
            val rawHeight = bounds.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return null

            val sample = sampleSize(rawWidth, rawHeight, targetPx)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

            val rotation = rotationOf(file)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated !== decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun renderPdfPage(file: File, targetPx: Int): Bitmap? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val pageWidth = page.width
                        val pageHeight = page.height
                        if (pageWidth <= 0 || pageHeight <= 0) return null

                        val scale = targetPx.toFloat() / pageWidth.toFloat()
                        val renderWidth = targetPx
                        val renderHeight = (pageHeight * scale).roundToInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun rotationOf(file: File): Int = try {
        when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Throwable) {
        0
    }

    /** Clears all cached thumbnails. */
    fun clear() {
        cache.evictAll()
    }
}

/**
 * Starts from the cache, so a tile scrolled back does not blink, and decodes on IO when missing.
 *
 * If the exact size is not yet cached but a fallback (e.g. 360 or 140) is in cache,
 * the fallback is displayed immediately while the targetPx resolution is decoded.
 */
@Composable
fun rememberThumbnail(memory: Memory, targetPx: Int): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(memory.id, memory.localUri, targetPx) {
        mutableStateOf(Thumbnails.cached(memory, targetPx))
    }

    LaunchedEffect(memory.id, memory.localUri, targetPx) {
        if (Thumbnails.cachedExact(memory, targetPx) == null) {
            // A few at a time: a decode cannot be cancelled once begun, so a fast
            // scroll would otherwise start dozens at once. A tile that has left by
            // its turn is cancelled before it starts.
            val loaded = withContext(Thumbnails.decoding) {
                Thumbnails.load(context, memory, targetPx)
            }
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    return bitmap
}
