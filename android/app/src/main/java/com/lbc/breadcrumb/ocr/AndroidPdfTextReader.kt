package com.lbc.breadcrumb.ocr

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device PDF text reader (reading workstream R3).
 *
 * - Android 15+ (API 35): extracts text layer using [PdfRenderer.Page.getTextContents].
 * - If no text layer, thin text layer, or older Android: runs OCR on the first 6 pages.
 * - If a text layer exists but is very thin and OCR provides clearly more text, uses OCR.
 * - Password-protected/encrypted PDFs return empty string without failing.
 * - Extracted text is capped at 20,000 characters.
 */
class AndroidPdfTextReader(
    private val context: Context,
    private val imageTextReader: ImageTextReader = MlKitImageTextReader(context),
) : PdfTextReader {

    override suspend fun readText(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return@withContext ""
        }

        val pfd = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: Throwable) {
            return@withContext ""
        }

        val renderer = try {
            PdfRenderer(pfd)
        } catch (_: SecurityException) {
            // Password-protected or encrypted PDF: returns empty string
            try { pfd.close() } catch (_: Throwable) {}
            return@withContext ""
        } catch (_: Throwable) {
            try { pfd.close() } catch (_: Throwable) {}
            return@withContext ""
        }

        try {
            val textLayer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                extractTextLayer(renderer)
            } else {
                null
            }

            // If we found a substantial text layer (e.g. >= 200 chars), we don't need OCR
            if (textLayer != null && !PdfTextRules.isThinText(textLayer)) {
                return@withContext textLayer.take(PdfTextRules.MAX_PDF_TEXT_LENGTH)
            }

            // If there's no text layer, older Android, or a thin text layer: run OCR on first 6 pages
            val ocrText = ocrFirstPages(renderer)

            PdfTextRules.selectBestText(textLayer, ocrText)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            ""
        } finally {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
        }
    }

    @TargetApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun extractTextLayer(renderer: PdfRenderer): String {
        val sb = StringBuilder()
        val pageCount = renderer.pageCount
        for (i in 0 until pageCount) {
            val page = try {
                renderer.openPage(i)
            } catch (_: Throwable) {
                continue
            }
            try {
                val textContents = page.textContents
                val pageText = textContents.joinToString("\n") { it.text.trim() }.trim()
                if (pageText.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append("\n\n")
                    sb.append(pageText)
                }
                if (sb.length >= PdfTextRules.MAX_PDF_TEXT_LENGTH) break
            } catch (_: Throwable) {
                // Ignore page read failure and continue with other pages
            } finally {
                try { page.close() } catch (_: Throwable) {}
            }
        }
        return sb.toString().trim()
    }

    private suspend fun ocrFirstPages(renderer: PdfRenderer): String {
        val sb = StringBuilder()
        val pagesToRead = minOf(renderer.pageCount, PdfTextRules.MAX_OCR_PAGES)
        for (i in 0 until pagesToRead) {
            val page = try {
                renderer.openPage(i)
            } catch (_: Throwable) {
                continue
            }
            val pageText = try {
                renderAndOcrPage(page)
            } finally {
                try { page.close() } catch (_: Throwable) {}
            }
            if (pageText.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(pageText)
            }
            if (sb.length >= PdfTextRules.MAX_PDF_TEXT_LENGTH) break
        }
        return sb.toString().trim()
    }

    private suspend fun renderAndOcrPage(page: PdfRenderer.Page): String {
        val origWidth = page.width
        val origHeight = page.height
        if (origWidth <= 0 || origHeight <= 0) return ""

        val scale = minOf(2.0f, 2048.0f / maxOf(origWidth, origHeight)).coerceAtLeast(1.0f)
        val width = (origWidth * scale).toInt()
        val height = (origHeight * scale).toInt()

        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return ""
        }

        try {
            // PDF backgrounds can be transparent; draw white so ML Kit has high contrast
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            return imageTextReader.readText(bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return ""
        } finally {
            bitmap.recycle()
        }
    }
}
