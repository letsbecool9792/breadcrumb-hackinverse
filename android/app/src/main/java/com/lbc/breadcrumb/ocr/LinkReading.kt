package com.lbc.breadcrumb.ocr

import android.content.Context
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.capture.TextRules
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.ReadingDao
import kotlinx.coroutines.CancellationException

/**
 * Link reading step (R4).
 *
 * Reads saved links on the phone, never the server.
 * Reads only the HTML <head>, up to </head> or 512 KB.
 * Extracts title (only if memory has none) and description into extractedText.
 *
 * Expose it as one step the upload queue runs first on each pass (`LinkReading`),
 * four pages at a time.
 */
class LinkReading(
    private val context: Context? = null,
    private val readingDao: ReadingDao = (context?.applicationContext as? BreadcrumbApp)?.database?.readingDao()
        ?: error("ReadingDao must be provided if context is null"),
    private val reader: LinkReader = HttpLinkReader(),
    private val scheduleUpload: (Context?) -> Unit = { ctx -> ctx?.let { uploadScheduler(it) } },
) {

    /**
     * Reads up to [limit] unread links (default 4).
     * Returns the number of links processed.
     */
    suspend fun readBatch(limit: Int = BATCH): Int {
        val batch = readingDao.getNewestUnreadLinks(limit)
        if (batch.isEmpty()) return 0

        var count = 0
        for (memory in batch) {
            try {
                if (processMemory(memory)) {
                    count++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // One bad row must not end the batch
            }
        }
        return count
    }

    /**
     * Reads all pending links, [batchSize] at a time, until none remain.
     * Used by the reading queue pass.
     */
    suspend fun readAllPending(batchSize: Int = BATCH): Int {
        var total = 0
        val tried = mutableSetOf<String>()
        while (true) {
            val batch = readingDao.getNewestUnreadLinks(batchSize)
                .filterNot { it.id in tried }
            if (batch.isEmpty()) break
            for (memory in batch) {
                tried += memory.id
                try {
                    if (processMemory(memory)) {
                        total++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // One bad row must not end the queue
                }
            }
        }
        return total
    }

    /**
     * Processes a single memory: extracts URL, reads page, updates Room.
     * Returns true if the memory was processed.
     */
    suspend fun processMemory(memory: Memory): Boolean {
        // Targeted check: ensure row still exists and is unread
        val current = readingDao.getById(memory.id) ?: return false
        if (current.extractedText != null) return false

        val rawText = current.rawText
        val url = rawText?.let { TextRules.firstUrl(it) }

        val result = if (!url.isNullOrBlank()) {
            try {
                reader.readLink(url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                LinkReadResult.EMPTY
            }
        } else {
            LinkReadResult.EMPTY
        }

        // Title preservation rule: only use fetched title if memory has none
        val titleToSet = if (current.title.isNullOrBlank()) {
            result.title?.takeIf { it.isNotBlank() }
        } else {
            null // Keep existing memory title
        }

        // Targeted UPDATE: only writes if row still exists and extractedText IS NULL
        val rowsUpdated = readingDao.updateLinkReading(
            id = current.id,
            extractedText = result.description,
            title = titleToSet,
        )

        if (rowsUpdated > 0) {
            scheduleUpload(context)
        }
        return rowsUpdated > 0
    }

    companion object {
        const val BATCH = 4
    }
}
