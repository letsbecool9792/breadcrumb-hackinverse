package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * What every workstream needs: writing, reading and deleting memories.
 *
 * Queries one workstream needs go in that workstream's own DAO (a ReadingDao,
 * an UploadDao, a SearchDao...), registered in [BreadcrumbDatabase], so no two
 * people edit this file at once.
 */
@Dao
interface MemoryDao {

    /**
     * Re-saving a row we already hold -- a retried import, a redelivered share
     * -- is idempotent on the client-generated id.
     *
     * @Upsert, never @Insert(REPLACE). REPLACE deletes the old row without
     * firing delete triggers, and those triggers are what keep [MemoryFts] in
     * step: the old text would stay in the search index. @Upsert updates in
     * place, which fires them.
     */
    @Upsert
    suspend fun upsert(memory: Memory)

    @Upsert
    suspend fun upsertAll(memories: List<Memory>)

    @Update
    suspend fun update(memory: Memory)

    /**
     * The row only. Almost every caller wants [deleteEverywhere], which also
     * tells the server.
     */
    @Delete
    suspend fun delete(memory: Memory)

    @Query("SELECT * FROM memories ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<Memory>>

    @Query("SELECT * FROM memories ORDER BY capturedAt DESC")
    suspend fun getAll(): List<Memory>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): Memory?

    @Query("SELECT * FROM memories WHERE id = :id")
    fun observeById(id: String): Flow<Memory?>

    /** Ask in chunks of a few hundred: SQLite counts bound values. */
    @Query("SELECT * FROM memories WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Memory>

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM memories")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun rememberDelete(delete: PendingDelete)

    @Query("DELETE FROM pending_deletes WHERE id = :id")
    suspend fun forgetDelete(id: String)

    /**
     * Deletes a memory here and remembers to delete it on the server, in one
     * transaction, so a crash between the two cannot leave a delete that never
     * reaches the cloud. The original file is the caller's to remove
     * ([OriginalStore.delete]).
     */
    @Transaction
    suspend fun deleteEverywhere(memory: Memory, now: Long) {
        delete(memory)
        rememberDelete(PendingDelete(memory.id, now))
    }

    /**
     * Undoes [deleteEverywhere]. The server may already have heard the delete,
     * so the caller restores the memory as PENDING: sending it again costs
     * nothing if the server still holds it, and restores it if not.
     */
    @Transaction
    suspend fun restore(memory: Memory) {
        upsert(memory)
        forgetDelete(memory.id)
    }

    @Query("INSERT OR REPLACE INTO pending_deletes (id, requestedAt) SELECT id, :now FROM memories")
    suspend fun rememberDeletingAll(now: Long)

    @Query("DELETE FROM memories")
    suspend fun clear()

    /** Every memory, here and on the server. */
    @Transaction
    suspend fun clearEverywhere(now: Long) {
        rememberDeletingAll(now)
        clear()
    }

    @Query("SELECT COUNT(*) FROM pending_deletes")
    suspend fun pendingDeleteCount(): Int
}
