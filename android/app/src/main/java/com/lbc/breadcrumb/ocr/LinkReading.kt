package com.lbc.breadcrumb.ocr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.capture.TextRules
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.ReadingDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException

/**
 * Link reading step (R4).
 *
 * Reads saved links on the phone, never the server.
 * Reads only the HTML <head>, up to </head> or 512 KB.
 * Extracts title (only if memory has none) and description into extractedText.
 *
 * The upload queue runs it first on each pass, four pages at a time, so a
 * link's page is read before its memory is sent. That is also why it waits
 * for the network: the upload queue only runs with one.
 *
 * Every answer is final (an error page, a login wall, a dead link while
 * online) except losing the network, which leaves the link for the next pass.
 */
class LinkReading(
    private val readingDao: ReadingDao,
    private val reader: LinkReader = HttpLinkReader(),
    /** Whether the phone is online, asked when a read fails. */
    private val isOnline: () -> Boolean = { true },
) {

    /** Over the app's database, asking Android whether the phone is online. */
    constructor(context: Context) : this(
        readingDao = (context.applicationContext as BreadcrumbApp).database.readingDao(),
        isOnline = { hasValidatedNetwork(context) },
    )

    /**
     * Reads up to [limit] unread links (default 4), at the same time.
     * Returns the number of links processed.
     */
    suspend fun readBatch(limit: Int = BATCH): Int = readTogether(readingDao.getNewestUnreadLinks(limit))

    /**
     * Reads all pending links, [batchSize] at a time, until none remain. A
     * link left unread (the network went) is not tried again in this pass.
     */
    suspend fun readAllPending(batchSize: Int = BATCH): Int {
        var total = 0
        val tried = mutableSetOf<String>()
        while (true) {
            val batch = readingDao.getNewestUnreadLinks(batchSize + tried.size)
                .filterNot { it.id in tried }
                .take(batchSize)
            if (batch.isEmpty()) break
            tried += batch.map { it.id }
            total += readTogether(batch)
        }
        return total
    }

    private suspend fun readTogether(batch: List<Memory>): Int = coroutineScope {
        batch.map { memory ->
            async {
                try {
                    processMemory(memory)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false // One bad row must not end the batch
                }
            }
        }.awaitAll().count { it }
    }

    /**
     * Processes a single memory: extracts URL, reads page, updates Room.
     * Returns true if the memory was processed, false if it is gone, already
     * read, or left for the next pass because the phone is offline.
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
            } catch (_: IOException) {
                // Offline, the link is not dead: it waits for the next pass.
                if (!isOnline()) return false
                LinkReadResult.EMPTY
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
        return rowsUpdated > 0
    }

    companion object {
        const val BATCH = 4

        /**
         * Whether the phone has a network Android has checked reaches the
         * internet. Without one, a failed read is the phone being offline, not
         * the link being dead.
         */
        fun hasValidatedNetwork(context: Context): Boolean {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}
