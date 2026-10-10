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
     */
    @Query("UPDATE memories SET extractedText = :extractedText, updatedAt = :updatedAt WHERE id = :id AND extractedText IS NULL")
    suspend fun updateExtractedText(
        id: String,
        extractedText: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int
}
