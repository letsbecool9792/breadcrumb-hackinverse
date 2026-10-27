package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.Embedding
import com.lbc.breadcrumb.data.EmbeddingDao
import com.lbc.breadcrumb.data.EmbeddingKey
import com.lbc.breadcrumb.data.EmbeddingsVersion
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.StoredVector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbedPassTest {

    private class Stored : EmbeddingDao {
        val rows = linkedMapOf<String, Embedding>()
        override suspend fun upsert(embedding: Embedding) { rows[embedding.memoryId] = embedding }
        override suspend fun keys() = rows.values.map { EmbeddingKey(it.memoryId, it.key) }
        override suspend fun vectors() = rows.values.map { StoredVector(it.memoryId, it.vector) }
        override suspend fun version() = EmbeddingsVersion(rows.size, rows.values.maxOfOrNull { it.embeddedAt })
        override suspend fun getAll() = rows.values.toList()
    }

    private class Model(var present: Boolean = true) : Embeddings {
        val texts = mutableListOf<String>()
        var pictures = 0
        override suspend fun text(prompt: String) = if (present) floatArrayOf(1f, 0f).also { texts += prompt } else null
        override suspend fun picture(jpeg: ByteArray, prompt: String?) = if (present) floatArrayOf(0f, 1f).also { pictures++ } else null
    }

    private val note = Memory(id = "note", type = MemoryType.TEXT, rawText = "brewery burger")
    private val photo = Memory(id = "photo", type = MemoryType.IMAGE, extractedText = "20:45")
    private val unread = Memory(id = "unread", type = MemoryType.IMAGE, extractedText = null)

    private fun pass(memories: List<Memory>, stored: Stored, model: Model, pictures: Boolean) = EmbedPass(
        memories = { memories },
        stored = stored,
        embeddings = model,
        pictureOf = { byteArrayOf(1) },
        hasPicture = { it.type == MemoryType.IMAGE },
        pictures = pictures,
        now = { 7L },
    )

    @Test
    fun `embeds what has no vector, and leaves what is being read`() = runTest {
        val stored = Stored()
        val outcome = pass(listOf(note, unread), stored, Model(), pictures = false).run()
        assertEquals(EmbedPass.Outcome.Done(embedded = 1, picturesWaiting = false), outcome)
        assertArrayEquals(Vectors.toBytes(floatArrayOf(1f, 0f)), stored.rows.getValue("note").vector)
    }

    @Test
    fun `a second pass over the same words embeds nothing`() = runTest {
        val stored = Stored()
        val model = Model()
        pass(listOf(note), stored, model, pictures = false).run()
        pass(listOf(note), stored, model, pictures = false).run()
        assertEquals(1, model.texts.size)
    }

    @Test
    fun `a changed note is embedded again`() = runTest {
        val stored = Stored()
        val model = Model()
        pass(listOf(note), stored, model, pictures = false).run()
        pass(listOf(note.copy(note = "Priya said")), stored, model, pictures = false).run()
        assertEquals(2, model.texts.size)
    }

    @Test
    fun `off the charger a barely read picture gets its words, and waits for its picture`() = runTest {
        val stored = Stored()
        val model = Model()
        val off = pass(listOf(photo), stored, model, pictures = false).run()
        assertEquals(EmbedPass.Outcome.Done(embedded = 1, picturesWaiting = true), off)
        assertEquals(0, model.pictures)

        // Again off the charger: its words are already in, nothing to do.
        pass(listOf(photo), stored, model, pictures = false).run()
        assertEquals(1, model.texts.size)

        val on = pass(listOf(photo), stored, model, pictures = true).run()
        assertEquals(EmbedPass.Outcome.Done(embedded = 1, picturesWaiting = false), on)
        assertEquals(1, model.pictures)
    }

    @Test
    fun `without the model it says so`() = runTest {
        val outcome = pass(listOf(note), Stored(), Model(present = false), pictures = false).run()
        assertEquals(EmbedPass.Outcome.NoModel, outcome)
    }

    @Test
    fun `vectors survive the round trip through room`() {
        val vector = floatArrayOf(0.25f, -1.5f, 3f)
        assertArrayEquals(vector, Vectors.fromBytes(Vectors.toBytes(vector)), 0f)
        assertEquals(0.25f * 0.25f + 1.5f * 1.5f + 9f, Vectors.dot(vector, vector), 1e-6f)
    }
}
