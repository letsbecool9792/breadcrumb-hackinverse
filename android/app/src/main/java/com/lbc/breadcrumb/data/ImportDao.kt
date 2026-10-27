package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Bulk importer duplicate tracking against [ImportedSource].
 *
 * Keeps keys for files (by name+size and sha256) and bookmarks (by URL)
 * that have been imported, so importing the same folder or export again
 * adds only what is new, and never resurrects memories that were deleted.
 */
@Dao
interface ImportDao {

    @Upsert
    suspend fun record(source: ImportedSource)

    @Upsert
    suspend fun recordAll(sources: List<ImportedSource>)

    @Query("SELECT EXISTS(SELECT 1 FROM imported_sources WHERE `key` = :key)")
    suspend fun exists(key: String): Boolean

    @Query("SELECT `key` FROM imported_sources WHERE `key` IN (:keys)")
    suspend fun findExistingKeys(keys: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM imported_sources")
    suspend fun count(): Int

    /** Everything taken in, for a backup: restored, the importer still skips it. */
    @Query("SELECT * FROM imported_sources")
    suspend fun getAll(): List<ImportedSource>

    /** The text of every memory carrying a link, to find the pages already saved by hand. */
    @Query("SELECT rawText FROM memories WHERE (type = 'LINK' OR hasLink = 1) AND rawText IS NOT NULL")
    suspend fun linkTexts(): List<String>

    @Query("DELETE FROM imported_sources")
    suspend fun clear()
}
