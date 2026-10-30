package com.lbc.breadcrumb.embed

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real model on the phone, against an in-memory database: memories are
 * embedded, a vague phrase finds the right one, and a vector goes with its
 * memory. Skipped when the model isn't on the phone.
 */
@RunWith(AndroidJUnit4::class)
class EmbedOnPhoneTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as BreadcrumbApp
    private val database = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
    }

    @Test
    fun aVaguePhraseFindsItsMemory() = runBlocking {
        assumeNotNull(app.models.ready())
        val embedder = Embedder(app.models::ready, context.cacheDir, scope)
        val burger = Memory(id = "burger", type = MemoryType.TEXT, rawText = "Try the brewery burger at that place in Indiranagar, Priya said")
        val job = Memory(id = "job", type = MemoryType.LINK, rawText = "https://www.linkedin.com/jobs/view/4120938817", title = "Software Engineering Intern, Qualcomm", extractedText = "")
        val cash = Memory(id = "cash", type = MemoryType.PDF, title = "FM Mod III", extractedText = "Cash Management. Motives for holding cash. Short term and long term cash forecasting")
        listOf(burger, job, cash).forEach { database.memoryDao().upsert(it) }

        val pass = EmbedPass(
            memories = { database.memoryDao().getAll() },
            stored = database.embeddingDao(),
            embeddings = embedder,
            pictureOf = { null },
            hasPicture = { false },
        )
        assertEquals(EmbedPass.Outcome.Done(embedded = 3), pass.run())

        val vectors = database.embeddingDao().vectors().associate { it.memoryId to Vectors.fromBytes(it.vector) }
        suspend fun best(phrase: String): String {
            val query = embedder.text(EmbedText.query(phrase))!!
            return vectors.maxBy { Vectors.dot(query, it.value) }.key
        }
        assertEquals("burger", best("where priya said to eat"))
        assertEquals("job", best("that internship i saved"))
        assertEquals("cash", best("finance lecture about managing money"))

        // The whole search on the phone, as the home screen runs it, without a parse.
        val local = LocalSearch(
            embed = { embedder.text(it) },
            vectors = VectorIndex(database.embeddingDao())::current,
            words = { query, limit -> database.readingDao().search(query, limit) },
            parse = { null },
            memories = { database.memoryDao().getAll() },
        )
        assertEquals("burger", local.search("where priya said to eat", 30).hits.first().id)
        assertEquals("job", local.search("qualcomm", 30).hits.first().id)

        database.memoryDao().delete(burger)
        assertEquals(2, database.embeddingDao().version().count)
        embedder.close()
    }
}
