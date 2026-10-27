package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A note said rather than written, on any memory (voice notes V4): a photo
 * with "Priya's flat, the one with the balcony" said over it. Its recording is
 * the file [OriginalStore.spokenFile] names, beside the memory's own; once it
 * is heard, what was said joins the memory's note, so word search and the
 * embedding find the memory by it. One a memory, and it goes with its memory.
 *
 * A table of its own (schema version 3), so the memories' rows are untouched.
 */
@Entity(
    tableName = "spoken_notes",
    foreignKeys = [
        ForeignKey(
            entity = Memory::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SpokenNote(
    @PrimaryKey val memoryId: String,
    val recordedAt: Long,
    /** Null until it is heard; then what was said is in the memory's note. */
    val heardAt: Long? = null,
)

@Dao
abstract class SpokenNoteDao {

    @Upsert
    abstract suspend fun upsert(note: SpokenNote)

    @Upsert
    abstract suspend fun upsertAll(notes: List<SpokenNote>)

    @Query("SELECT * FROM spoken_notes WHERE memoryId = :memoryId")
    abstract suspend fun get(memoryId: String): SpokenNote?

    @Query("SELECT * FROM spoken_notes WHERE memoryId = :memoryId")
    abstract fun observe(memoryId: String): Flow<SpokenNote?>

    @Query("SELECT * FROM spoken_notes")
    abstract suspend fun getAll(): List<SpokenNote>

    @Query("DELETE FROM spoken_notes WHERE memoryId = :memoryId")
    abstract suspend fun delete(memoryId: String): Int

    /** The notes not heard yet, by memory, newest first, for the listen queue to watch. */
    @Query("SELECT memoryId FROM spoken_notes WHERE heardAt IS NULL ORDER BY recordedAt DESC")
    abstract fun observeUnheard(): Flow<List<String>>

    @Query("SELECT * FROM spoken_notes WHERE heardAt IS NULL ORDER BY recordedAt DESC LIMIT :limit")
    abstract suspend fun getNewestUnheard(limit: Int): List<SpokenNote>

    /**
     * It is heard: what was said goes under the memory's note (as the capture
     * sheet's "add another note" does), in the same transaction, once. The rows
     * of spoken notes marked heard: 0 when it was heard already, or is gone.
     */
    @Transaction
    open suspend fun heard(memoryId: String, words: String, at: Long = System.currentTimeMillis()): Int {
        val marked = markHeard(memoryId, at)
        if (marked > 0 && words.isNotBlank()) addToNote(memoryId, words.trim(), at)
        return marked
    }

    @Query("UPDATE spoken_notes SET heardAt = :at WHERE memoryId = :memoryId AND heardAt IS NULL")
    protected abstract suspend fun markHeard(memoryId: String, at: Long): Int

    @Query(
        """
        UPDATE memories
        SET note = CASE WHEN note IS NULL OR note = '' THEN :words ELSE note || char(10) || :words END,
            updatedAt = :at
        WHERE id = :memoryId
        """,
    )
    protected abstract suspend fun addToNote(memoryId: String, words: String, at: Long)
}
