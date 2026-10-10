package com.lbc.breadcrumb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Something a bulk import has already taken in -- a file in a folder, a
 * bookmark in an export.
 *
 * Kept apart from the memory it became, and outliving it: importing the same
 * folder again should add what is new and nothing else, and must never bring
 * back what was imported and then deleted.
 *
 * The table is here from the start so the schema is whole; its DAO is the
 * importer's (reading workstream).
 */
@Entity(tableName = "imported_sources")
data class ImportedSource(
    /** A stable key for the thing imported, as the importer names it. */
    @PrimaryKey val key: String,
    val importedAt: Long,
)
