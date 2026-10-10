package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Query

/**
 * Capture workstream: writes to memories the capture sheet has just saved.
 *
 * Registered in [BreadcrumbDatabase].
 */
@Dao
interface CaptureDao {

    /**
     * Puts the capture sheet's note on [ids], and touches nothing else. Never
     * the row as the sheet saved it: by the time the sheet goes, the OCR queue
     * may already have written a picture's text, and writing the saved row
     * back would wipe it. Like any UPDATE, it keeps the word index in step.
     */
    @Query("UPDATE memories SET note = :note, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setNote(ids: List<String>, note: String, updatedAt: Long = System.currentTimeMillis())
}
