package com.lbc.breadcrumb.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * A memory's vector, made on the phone (local-first L1). A table of its own, not
 * a column on [Memory]: every read of the memories (the mosaic's, the upload
 * queue's) would otherwise carry 3 KB of floats per row. Goes with its memory.
 */
@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Memory::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class Embedding(
    @PrimaryKey val memoryId: String,
    /**
     * Which model embedded what ([com.lbc.breadcrumb.embed.EmbedText.want]): a
     * memory whose wanted key differs is embedded again, after a read finishes
     * or a note changes.
     */
    val key: String,
    /** Normalised, as little-endian floats ([com.lbc.breadcrumb.embed.Vectors]). */
    val vector: ByteArray,
    val embeddedAt: Long,
)

class EmbeddingKey(val memoryId: String, val key: String)

class StoredVector(val memoryId: String, val vector: ByteArray)

/** Changes whenever a vector is written or goes with its memory, so the ranking knows to reload. */
data class EmbeddingsVersion(val count: Int, val latest: Long?)

@Dao
interface EmbeddingDao {

    @Upsert
    suspend fun upsert(embedding: Embedding)

    @Query("SELECT memoryId, `key` FROM embeddings")
    suspend fun keys(): List<EmbeddingKey>

    @Query("SELECT memoryId, vector FROM embeddings")
    suspend fun vectors(): List<StoredVector>

    @Query("SELECT COUNT(*) AS count, MAX(embeddedAt) AS latest FROM embeddings")
    suspend fun version(): EmbeddingsVersion
}
