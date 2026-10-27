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
     *
     * Only where the note actually changes, so an unchanged note costs no new
     * embedding. (syncState PENDING is the retired upload queue's mark; nothing
     * reads it since local-first L7.) Returns the rows changed.
     */
    @Query(
        """
        UPDATE memories
        SET note = :note, updatedAt = :updatedAt, syncState = 'PENDING'
        WHERE id IN (:ids) AND IFNULL(note, '') != IFNULL(:note, '')
        """,
    )
    suspend fun setNote(ids: List<String>, note: String?, updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * Adds [note] under the note [ids] already have, on a line of its own, or
     * as their note when they have none: the sheet's "add another note" on a
     * memory kept already (roadmap 1). Returns the rows changed.
     */
    @Query(
        """
        UPDATE memories
        SET note = CASE WHEN note IS NULL OR note = '' THEN :note ELSE note || char(10) || :note END,
            updatedAt = :updatedAt
        WHERE id IN (:ids)
        """,
    )
    suspend fun addNote(ids: List<String>, note: String, updatedAt: Long = System.currentTimeMillis()): Int

    /** Every memory of [type], newest first: what a save is compared with, to say it is kept already. */
    @Query("SELECT * FROM memories WHERE type = :type ORDER BY capturedAt DESC")
    suspend fun ofType(type: MemoryType): List<Memory>
}
