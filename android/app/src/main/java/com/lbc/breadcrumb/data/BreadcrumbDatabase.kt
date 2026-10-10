package com.lbc.breadcrumb.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Schema changes go through a version bump and an AutoMigration against the
 * checked-in schema JSON (app/schemas/). Never fallbackToDestructiveMigration:
 * it would silently wipe saved memories on upgrade. A migration that adds or
 * changes the full-text index's columns must also rebuild it
 * (`INSERT INTO memories_fts(memories_fts) VALUES('rebuild')` in the spec's
 * onPostMigrate), since its triggers only see later writes.
 *
 * Each workstream adds its own DAO here: one abstract function per DAO.
 */
@Database(
    entities = [Memory::class, MemoryFts::class, PendingDelete::class, ImportedSource::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BreadcrumbDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao

    // Reading workstream: unread image queries and targeted text updates
    abstract fun readingDao(): ReadingDao

    // Capture workstream: the capture sheet's note, written by id
    abstract fun captureDao(): CaptureDao

    // Reading workstream: bulk importer duplicate tracking
    abstract fun importDao(): ImportDao

    companion object {
        private const val DB_NAME = "breadcrumb.db"

        @Volatile
        private var instance: BreadcrumbDatabase? = null

        fun get(context: Context): BreadcrumbDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                BreadcrumbDatabase::class.java,
                DB_NAME,
            ).build()
    }
}
