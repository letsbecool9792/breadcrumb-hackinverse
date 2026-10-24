package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
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

    /** The row; its vector goes with it. The original file is the caller's to remove ([OriginalStore.delete]). */
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

    @Query("DELETE FROM memories")
    suspend fun clear()
}
