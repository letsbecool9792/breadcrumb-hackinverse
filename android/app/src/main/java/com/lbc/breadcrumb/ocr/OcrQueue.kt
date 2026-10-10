package com.lbc.breadcrumb.ocr

import android.content.Context
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.data.ReadingDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background OCR queue for saved pictures.
 *
 * Starts in [BreadcrumbApp.onCreate] in `applicationScope` in every process.
 * Watches Room for unread IMAGE rows and reads them newest first.
 *
 * Presumes [readingOriginals] is true until it has looked at Room, and sets it
 * false when there are no unread pictures left or in a finally block if the queue
 * stops or fails to start.
 */
class OcrQueue(
    private val context: Context,
    private val readingDao: ReadingDao = (context.applicationContext as BreadcrumbApp).database.readingDao(),
    private val originals: OriginalStore = (context.applicationContext as BreadcrumbApp).originals,
    private val scope: CoroutineScope = (context.applicationContext as BreadcrumbApp).applicationScope,
    private val readingOriginals: MutableStateFlow<Boolean> = (context.applicationContext as BreadcrumbApp).readingOriginals,
    private val reader: ImageTextReader = MlKitImageTextReader(context),
    private val scheduleUpload: (Context) -> Unit = { uploadScheduler(it) },
) {
    private val mutex = Mutex()

    fun start(): Job = scope.launch {
        try {
            readingDao.observeUnreadImages().collect { unread ->
                mutex.withLock {
                    if (unread.isEmpty()) {
                        readingOriginals.value = false
                    } else {
                        readingOriginals.value = true
                        for (memory in unread) {
                            processMemory(memory)
                        }
                        val remaining = readingDao.countUnreadImages()
                        readingOriginals.value = remaining > 0
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Error in queue loop must not crash the process; finally sets readingOriginals false
        } finally {
            readingOriginals.value = false
        }
    }

    private suspend fun processMemory(memory: Memory) {
        // Check if row still exists and is still unread
        val current = readingDao.getById(memory.id) ?: return
        if (current.extractedText != null) return

        val file = originals.fileFor(current)
        val text = if (file != null && file.exists()) {
            try {
                reader.readText(file)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                "" // Unreadable image marked empty so it never blocks
            }
        } else {
            "" // Missing or unresolved file marked empty
        }

        // Targeted UPDATE: only writes if row still exists and extractedText IS NULL.
        // If deleted mid-read, updates 0 rows and stays deleted.
        val rowsUpdated = readingDao.updateExtractedText(current.id, text)
        if (rowsUpdated > 0) {
            scheduleUpload(context)
        }
    }
}
