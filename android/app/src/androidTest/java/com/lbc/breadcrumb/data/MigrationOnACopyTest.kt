package com.lbc.breadcrumb.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * A schema bump against a copy of a real phone's database: every row of every table is
 * the same after the migration as before, and word search still finds a memory by its words.
 * Skipped unless a copy is there. It never opens the app's own database.
 *
 * Before a schema bump, take a standalone copy of the phone's database (`VACUUM INTO`, from a
 * backup: see CLAUDE.md) and push it to the app's external files directory:
 * `adb push breadcrumb-v2-copy.db /sdcard/Android/data/com.lbc.breadcrumb/files/`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationOnACopyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val copy = File(context.cacheDir, "migration-copy.db")

    @After
    fun remove() {
        for (ending in listOf("", "-wal", "-shm", "-journal")) File(copy.path + ending).delete()
    }

    @Test
    fun aRealDatabaseMigratesWithEveryRowAsItWas() = runBlocking {
        val pushed = context.getExternalFilesDir(null)?.let { File(it, "breadcrumb-v2-copy.db") }
        assumeTrue("no copy pushed", pushed?.canRead() == true)
        pushed!!.copyTo(copy, overwrite = true)
        val before = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { rows(it::rawQuery) }

        val db = Room.databaseBuilder(context, BreadcrumbDatabase::class.java, copy.absolutePath).build()
        try {
            val sqlite: SupportSQLiteDatabase = db.openHelper.writableDatabase
            assertEquals(3, sqlite.version)
            val after = rows { sql, _ -> sqlite.query(sql) }
            assertEquals(before.keys, after.keys)
            for (table in before.keys) assertEquals("$table changed", before[table], after[table])
            assertTrue("memories were there to compare", before.getValue("memories").isNotEmpty())
            assertEquals(0, db.spokenNoteDao().getAll().size)

            // The word index came through: a memory is found by a word of its own.
            val memory = db.memoryDao().getAll().first { word(it) != null }
            assertTrue(db.readingDao().search(word(memory)!!).any { it.id == memory.id })
        } finally {
            db.close()
        }
    }

    /** Every row of every table there was before, as text (a blob by its hash), in rowid order. */
    private fun rows(query: (String, Array<String>?) -> Cursor): Map<String, List<String>> =
        TABLES.associateWith { table ->
            query("SELECT * FROM $table ORDER BY rowid", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            (0 until cursor.columnCount).joinToString("|") { i ->
                                when (cursor.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> "null"
                                    Cursor.FIELD_TYPE_BLOB -> MessageDigest.getInstance("SHA-256").digest(cursor.getBlob(i))
                                        .joinToString("") { "%02x".format(it) }
                                    else -> cursor.getString(i)
                                }
                            },
                        )
                    }
                }
            }
        }

    private fun word(memory: Memory): String? =
        Regex("""\p{L}{5,}""").find(memory.searchableText)?.value

    private companion object {
        val TABLES = listOf("memories", "embeddings", "imported_sources", "pending_deletes")
    }
}
