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

    /** The one picture the OCR queue reads next: the newest still unread. */
    @Query("SELECT * FROM memories WHERE type = 'IMAGE' AND extractedText IS NULL ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getNewestUnreadImage(): Memory?

    @Query("SELECT COUNT(*) FROM memories WHERE type = 'IMAGE' AND extractedText IS NULL")
    suspend fun countUnreadImages(): Int

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): Memory?

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
