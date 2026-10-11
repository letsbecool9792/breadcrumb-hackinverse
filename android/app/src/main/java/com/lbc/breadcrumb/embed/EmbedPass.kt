package com.lbc.breadcrumb.embed

import android.database.sqlite.SQLiteConstraintException
import com.lbc.breadcrumb.data.Embedding
import com.lbc.breadcrumb.data.EmbeddingDao
import com.lbc.breadcrumb.data.Memory
import kotlinx.coroutines.CancellationException

/**
 * One pass over every memory, newest first: embeds what has no vector, or one
 * made from other words or by another model. A picture OCR could barely read is
 * embedded as a picture only when [pictures] allows it (on the charger);
 * meanwhile its few words stand in.
 */
class EmbedPass(
    private val memories: suspend () -> List<Memory>,
    private val stored: EmbeddingDao,
    private val embeddings: Embeddings,
    private val pictureOf: (Memory) -> ByteArray?,
    private val hasPicture: (Memory) -> Boolean,
    private val pictures: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {

    sealed interface Outcome {
        /** Everything has its vector, or will once the phone is charging. */
        data class Done(val embedded: Int, val picturesWaiting: Boolean) : Outcome

        /** The model isn't on the phone yet. */
        data object NoModel : Outcome
    }

    suspend fun run(): Outcome {
        val have = stored.keys().associate { it.memoryId to it.key }
        var embedded = 0
        var picturesWaiting = false
        for (memory in memories()) {
            val want = EmbedText.want(memory, hasPicture(memory)) ?: continue
            if (have[memory.id] == want.key) continue
            val doing = when {
                want is EmbedText.AsPicture && !pictures -> {
                    picturesWaiting = true
                    want.meanwhile?.takeIf { have[memory.id] != it.key } ?: continue
                }
                else -> want
            }
            val vector = try {
                embed(memory, doing) ?: return Outcome.NoModel
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The model refused this one (too long, a picture it can't decode): the rest still go.
                continue
            }
            try {
                stored.upsert(Embedding(memory.id, doing.key, Vectors.toBytes(vector), now()))
                embedded++
            } catch (_: SQLiteConstraintException) {
                // Deleted while it was being embedded: its vector goes with it.
            }
        }
        return Outcome.Done(embedded, picturesWaiting)
    }

    private suspend fun embed(memory: Memory, want: EmbedText.Want): FloatArray? = when (want) {
        is EmbedText.AsText -> embeddings.text(want.prompt)
        is EmbedText.AsPicture -> {
            val jpeg = pictureOf(memory)
            when {
                jpeg != null -> embeddings.picture(jpeg, want.prompt)
                want.meanwhile != null -> embeddings.text(want.meanwhile.prompt)
                else -> throw IllegalStateException("no picture and no words")
            }
        }
    }
}
