package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Sync workstream: what the upload queue claims, sends and marks.
 *
 * Every mark that ends an upload moves only rows still UPLOADING. Reading's
 * `updateExtractedText` and capture's `setNote` put a row back to PENDING
 * whatever its state, because the server has not seen the change; a row they
 * touch mid-upload must stay PENDING for the next pass, not be marked sent.
 *
 * Registered in [BreadcrumbDatabase].
 */
@Dao
interface UploadDao {

    /** A pass starts with nothing in flight, so an UPLOADING row is one a killed process left. */
    @Query("UPDATE memories SET syncState = 'PENDING' WHERE syncState = 'UPLOADING'")
    suspend fun releaseStale(): Int

    @Query("SELECT id FROM pending_deletes ORDER BY requestedAt LIMIT :limit")
    suspend fun pendingDeletes(limit: Int): List<String>

    @Query("DELETE FROM pending_deletes WHERE id IN (:ids)")
    suspend fun forgetDeletes(ids: List<String>)

    /**
     * The next [limit] memories to send, oldest first: PENDING, and not held
     * back. Held back: [held] (the capture sheet's, while its note field is
     * open), and an unread picture or PDF while [reading] is true, or saved
     * after [unreadSince] (the grace after reading stops, for a read that
     * failed). Held in the query, not after it, so ten held rows at the front
     * cannot stop everything behind them.
     */
    @Query(
        """
        SELECT * FROM memories
        WHERE syncState = 'PENDING'
          AND id NOT IN (:held)
          AND NOT (type IN ('IMAGE', 'PDF') AND extractedText IS NULL AND (:reading OR capturedAt > :unreadSince))
        ORDER BY capturedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun nextToSend(held: List<String>, reading: Boolean, unreadSince: Long, limit: Int): List<Memory>

    @Query("UPDATE memories SET syncState = 'UPLOADING' WHERE id IN (:ids) AND syncState = 'PENDING'")
    suspend fun markUploading(ids: List<String>)

    /** [nextToSend], marked UPLOADING in the same transaction, so what is sent is what was claimed. */
    @Transaction
    suspend fun claimNext(held: List<String>, reading: Boolean, unreadSince: Long, limit: Int): List<Memory> =
        nextToSend(held, reading, unreadSince, limit).also { batch ->
            if (batch.isNotEmpty()) markUploading(batch.map { it.id })
        }

    /**
     * Stored on the server. Only from UPLOADING (see the class). Sending
     * clears [Memory.enrichedAt]: the server may have read it again.
     */
    @Query("UPDATE memories SET syncState = 'SYNCED', remoteId = id, enrichedAt = NULL WHERE id IN (:ids) AND syncState = 'UPLOADING'")
    suspend fun markSynced(ids: List<String>)

    /** Back in the queue: the server said later, or never answered. */
    @Query("UPDATE memories SET syncState = 'PENDING' WHERE id IN (:ids) AND syncState = 'UPLOADING'")
    suspend fun markPending(ids: List<String>)

    /** Refused: the same bytes would be refused again, so not retried until [retryFailed]. */
    @Query("UPDATE memories SET syncState = 'FAILED' WHERE id IN (:ids) AND syncState = 'UPLOADING'")
    suspend fun markFailed(ids: List<String>)

    /** The debug list's button: refused memories go in the queue again. */
    /** Cloud reading turned off and the server told to forget: everything is unsent again, so turning it on sends it all. */
    @Query("UPDATE memories SET syncState = 'PENDING', remoteId = NULL, imageSentAt = NULL WHERE syncState != 'PENDING'")
    suspend fun unsendAll(): Int

    @Query("UPDATE memories SET syncState = 'PENDING' WHERE syncState = 'FAILED'")
    suspend fun retryFailed(): Int

    /**
     * Pictures to send, oldest first: only after their memory is on the
     * server, only once, and only where OCR read under [minChars] characters
     * (a text-heavy screenshot is left to its words). An unread one is held
     * back as in [nextToSend].
     */
    @Query(
        """
        SELECT * FROM memories
        WHERE type = 'IMAGE' AND syncState = 'SYNCED' AND imageSentAt IS NULL AND localUri IS NOT NULL
          AND (length(extractedText) < :minChars OR (extractedText IS NULL AND NOT :reading AND capturedAt <= :unreadSince))
        ORDER BY capturedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun picturesToSend(minChars: Int, reading: Boolean, unreadSince: Long, limit: Int): List<Memory>

    /**
     * Sent once, never again. Clears [Memory.enrichedAt], since what the model
     * saw in the picture changes the summary.
     */
    @Query("UPDATE memories SET imageSentAt = :now, enrichedAt = NULL WHERE id IN (:ids)")
    suspend fun markPicturesSent(ids: List<String>, now: Long)

    /** Synced memories whose copy of the server's reading is stale. */
    @Query("SELECT id FROM memories WHERE syncState = 'SYNCED' AND enrichedAt IS NULL ORDER BY capturedAt ASC LIMIT :limit")
    suspend fun toEnrich(limit: Int): List<String>

    @Query("UPDATE memories SET summary = :summary, kind = :kind, readText = :readText WHERE id = :id")
    suspend fun copyReading(id: String, summary: String?, kind: String?, readText: String?)

    @Query("UPDATE memories SET enrichedAt = :now WHERE id IN (:ids)")
    suspend fun markEnriched(ids: List<String>, now: Long)

    /**
     * Copies [readings] back, and marks every id [asked] as copied, answered
     * or not, so the pass cannot ask for the same ones forever. One not
     * enriched yet is asked again only after it is next sent.
     */
    @Transaction
    suspend fun copyEnrichment(asked: List<String>, readings: List<ServerReading>, now: Long) {
        for (reading in readings) copyReading(reading.id, reading.summary, reading.kind, reading.readText)
        markEnriched(asked, now)
    }

    /**
     * When the oldest unread picture or PDF still waiting was saved: the pass
     * looks again once its grace is over, since a read that failed calls
     * nobody.
     */
    @Query("SELECT MIN(capturedAt) FROM memories WHERE syncState = 'PENDING' AND type IN ('IMAGE', 'PDF') AND extractedText IS NULL")
    suspend fun oldestUnreadWaiting(): Long?

    /**
     * Memories not yet SYNCED (PENDING, UPLOADING, FAILED).
     */
    @Query("SELECT COUNT(*) FROM memories WHERE syncState != 'SYNCED'")
    fun observeUnsentCount(): Flow<Int>
}

/** What the server made of one memory, as the phone keeps it. */
data class ServerReading(val id: String, val summary: String?, val kind: String?, val readText: String?)
