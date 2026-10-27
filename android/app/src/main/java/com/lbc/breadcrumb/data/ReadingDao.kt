package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reading workstream: queries for unread memories and targeted text updates.
 *
 * Registered in [BreadcrumbDatabase].
 */
@Dao
interface ReadingDao {

    /**
     * Unread images, newest first, for [com.lbc.breadcrumb.ocr.OcrQueue].
     *
     * Watches Room so any newly inserted IMAGE row gets read without calling
     * anything directly.
     */
    @Query("SELECT * FROM memories WHERE type = 'IMAGE' AND extractedText IS NULL ORDER BY capturedAt DESC")
    fun observeUnreadImages(): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE type = 'IMAGE' AND extractedText IS NULL ORDER BY capturedAt DESC")
    suspend fun getUnreadImages(): List<Memory>

    /** The pictures the OCR queue reads next: the newest [limit] still unread. */
    @Query("SELECT * FROM memories WHERE type = 'IMAGE' AND extractedText IS NULL ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun getNewestUnreadImages(limit: Int): List<Memory>

    @Query("SELECT COUNT(*) FROM memories WHERE type = 'IMAGE' AND extractedText IS NULL")
    suspend fun countUnreadImages(): Int

    /**
     * Unread images and PDFs, newest first, for [com.lbc.breadcrumb.ocr.OcrQueue].
     *
     * Watches Room so any newly inserted IMAGE or PDF row gets read without
     * calling anything directly.
     */
    @Query("SELECT * FROM memories WHERE type IN ('IMAGE', 'PDF') AND extractedText IS NULL ORDER BY capturedAt DESC")
    fun observeUnreadMedia(): Flow<List<Memory>>

    /** The pictures and PDFs the OCR queue reads next: the newest [limit] still unread. */
    @Query("SELECT * FROM memories WHERE type IN ('IMAGE', 'PDF') AND extractedText IS NULL ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun getNewestUnreadMedia(limit: Int): List<Memory>

    /** The voice notes not heard yet, by id, newest first, for [com.lbc.breadcrumb.voice.ListenQueue] to watch. */
    @Query("SELECT id FROM memories WHERE type = 'AUDIO' AND extractedText IS NULL ORDER BY capturedAt DESC")
    fun observeUnheard(): Flow<List<String>>

    /** The voice notes the listen queue hears next: the newest [limit] not heard yet. */
    @Query("SELECT * FROM memories WHERE type = 'AUDIO' AND extractedText IS NULL ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun getNewestUnheard(limit: Int): List<Memory>

    /** The links [com.lbc.breadcrumb.ocr.LinkReading] reads next: the newest [limit] still unread. */
    @Query("SELECT * FROM memories WHERE (type = 'LINK' OR (hasLink = 1 AND type = 'TEXT')) AND extractedText IS NULL ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun getNewestUnreadLinks(limit: Int): List<Memory>

    @Query("SELECT COUNT(*) FROM memories WHERE (type = 'LINK' OR (hasLink = 1 AND type = 'TEXT')) AND extractedText IS NULL")
    suspend fun countUnreadLinks(): Int

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): Memory?

    /** Links already read that have no preview picture yet, newest first. */
    @Query("SELECT * FROM memories WHERE type = 'LINK' AND localUri IS NULL AND extractedText IS NOT NULL ORDER BY capturedAt DESC")
    suspend fun linksWithoutPreview(): List<Memory>

    /** A link's preview picture, kept in the originals folder like any picture. */
    @Query("UPDATE memories SET localUri = :localUri, updatedAt = :updatedAt WHERE id = :id AND type = 'LINK' AND localUri IS NULL")
    suspend fun setLinkPreview(id: String, localUri: String, updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * A title read later (a video's, from oEmbed; a walled page's, read again), only where there was
     * none or only [bare], the site's name; its words too, where there were none.
     */
    @Query(
        """
        UPDATE memories SET title = :title,
            extractedText = CASE WHEN extractedText IS NULL OR extractedText = '' THEN :text ELSE extractedText END,
            updatedAt = :updatedAt
        WHERE id = :id AND (title IS NULL OR title = '' OR title = :bare)
        """,
    )
    suspend fun fillLinkTitle(id: String, title: String, text: String, bare: String = "", updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * Targeted update of [Memory.extractedText].
     *
     * Only updates if the row still exists and is currently unread (extractedText IS NULL).
     * If the memory was deleted mid-read, updates 0 rows and stays deleted (never
     * resurrects a deleted row).
     *
     * Fires Room's FTS BEFORE_UPDATE and AFTER_UPDATE triggers to keep [MemoryFts]
     * in step.
     *
     * Text puts the memory back in the upload queue (syncState PENDING), whatever
     * state it was in: the server has not read it. An empty read does not: it
     * changes nothing the server reads, and sending the memory again would cost a
     * model call for no new words.
     */
    @Query(
        """
        UPDATE memories
        SET extractedText = :extractedText,
            updatedAt = :updatedAt,
            syncState = CASE WHEN :extractedText != '' THEN 'PENDING' ELSE syncState END
        WHERE id = :id AND extractedText IS NULL
        """,
    )
    suspend fun updateExtractedText(
        id: String,
        extractedText: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    /**
     * Targeted update of [Memory.extractedText] and [Memory.title] for a read link.
     *
     * Only updates if the row still exists and is currently unread (extractedText IS NULL).
     * If the memory already has a non-empty title, preserves it (e.g. Chrome share subject).
     * If the memory was deleted mid-read, updates 0 rows and stays deleted.
     *
     * Fires Room's FTS BEFORE_UPDATE and AFTER_UPDATE triggers.
     * Non-empty text or newly set non-empty title puts the memory back to PENDING.
     */
    @Query(
        """
        UPDATE memories
        SET extractedText = :extractedText,
            title = CASE WHEN title IS NULL OR trim(title) = '' THEN :title ELSE title END,
            updatedAt = :updatedAt,
            syncState = CASE WHEN :extractedText != '' OR (:title IS NOT NULL AND :title != '' AND (title IS NULL OR trim(title) = '')) THEN 'PENDING' ELSE syncState END
        WHERE id = :id AND extractedText IS NULL
        """,
    )
    suspend fun updateLinkReading(
        id: String,
        extractedText: String,
        title: String?,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    /**
     * Raw FTS4 search query matching against [MemoryFts].
     *
     * Callers must always pass input through [FtsQuery.matchExpression]
     * rather than invoking this query with arbitrary user input directly.
     * Ordered newest first ([Memory.capturedAt] DESC) with limit.
     */
    @Query("""
        SELECT memories.* FROM memories
        JOIN memories_fts ON memories.rowid = memories_fts.docid
        WHERE memories_fts MATCH :matchQuery
        ORDER BY memories.capturedAt DESC
        LIMIT :limit
    """)
    fun searchByMatch(matchQuery: String, limit: Int = 50): Flow<List<Memory>>

    @Query("""
        SELECT memories.* FROM memories
        JOIN memories_fts ON memories.rowid = memories_fts.docid
        WHERE memories_fts MATCH :matchQuery
        ORDER BY memories.capturedAt DESC
        LIMIT :limit
    """)
    suspend fun searchByMatchOnce(matchQuery: String, limit: Int = 50): List<Memory>

    /**
     * Live word search over [MemoryFts].
     *
     * Input always goes through [FtsQuery.matchExpression] before reaching MATCH.
     * If the query has no searchable words, emits an empty list.
     * Ordered newest first.
     */
    fun observeSearch(query: String, limit: Int = 50): Flow<List<Memory>> {
        val matchExpr = FtsQuery.matchExpression(query)
        return if (matchExpr != null) {
            searchByMatch(matchExpr, limit)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    /**
     * One-shot suspend word search over [MemoryFts].
     *
     * Input always goes through [FtsQuery.matchExpression] before reaching MATCH.
     * If the query has no searchable words, returns an empty list.
     * Ordered newest first.
     */
    suspend fun search(query: String, limit: Int = 50): List<Memory> {
        val matchExpr = FtsQuery.matchExpression(query) ?: return emptyList()
        return searchByMatchOnce(matchExpr, limit)
    }
}
