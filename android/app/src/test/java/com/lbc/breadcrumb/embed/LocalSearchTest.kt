package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.net.Interpretation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LocalSearchTest {

    private val april = LocalDate.parse("2026-04-12").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val june = LocalDate.parse("2026-06-02").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val burger = Memory(id = "burger", type = MemoryType.TEXT, rawText = "brewery burger", capturedAt = june, sourceAppLabel = "WhatsApp")
    private val intern = Memory(id = "intern", type = MemoryType.IMAGE, extractedText = "internship", capturedAt = june, contentCreatedAt = april)
    private val job = Memory(id = "job", type = MemoryType.LINK, rawText = "https://www.linkedin.com/jobs/view/1", capturedAt = april)
    private val captioned = Memory(id = "captioned", type = MemoryType.IMAGE, hasLink = true, extractedText = "x", capturedAt = june)
    private val far = Memory(id = "far", type = MemoryType.TEXT, rawText = "unrelated", capturedAt = june)
    private val all = listOf(burger, intern, job, captioned, far)

    // Unit vectors, so the dot product is easy to read: the query points at "burger".
    private val vectors = listOf(
        "burger" to floatArrayOf(1f, 0f),
        "intern" to floatArrayOf(0.95f, 0.31f),
        "job" to floatArrayOf(0.9f, 0.43f),
        "captioned" to floatArrayOf(0.92f, 0.39f),
        "far" to floatArrayOf(0f, 1f),
    )

    private fun search(
        parse: suspend (String) -> Interpretation? = { null },
        words: List<Memory> = emptyList(),
        model: Boolean = true,
    ) = LocalSearch(
        embed = { if (model) floatArrayOf(1f, 0f) else null },
        vectors = { vectors },
        words = { _, _ -> words },
        parse = parse,
        memories = { all },
        zone = ZoneOffset.UTC,
    )

    @Test
    fun `memories are ranked by meaning, and the far ones left out`() = runTest {
        val found = search().search("where priya said to eat", 30)!!
        assertEquals(listOf("burger", "intern", "captioned", "job"), found.hits.map { it.id })
    }

    @Test
    fun `a memory placed by both halves rises above one placed first by only one`() = runTest {
        val found = search(words = listOf(job, intern)).search("job", 30)!!
        assertEquals("intern", found.hits.first().id)
        assertEquals(2, found.hits.first().ranks?.vector)
        assertEquals(2, found.hits.first().ranks?.text)
    }

    @Test
    fun `the parse narrows by type, and a link filter takes anything carrying a link`() = runTest {
        val links = search(parse = { Interpretation(query = "x", types = listOf("LINK")) }).search("that link", 30)!!
        assertEquals(setOf("job", "captioned"), links.hits.map { it.id }.toSet())
    }

    @Test
    fun `dates are the content's own, or when it was saved`() = runTest {
        val inApril = search(parse = { Interpretation(from = "2026-04-01", to = "2026-04-30") }).search("from april", 30)!!
        assertEquals(setOf("intern", "job"), inApril.hits.map { it.id }.toSet())
    }

    @Test
    fun `a source is the app it came from, or a link's site`() = runTest {
        val whatsapp = search(parse = { Interpretation(sourceApp = "whatsapp") }).search("on whatsapp", 30)!!
        assertEquals(listOf("burger"), whatsapp.hits.map { it.id })
        val linkedin = search(parse = { Interpretation(sourceApp = "LinkedIn") }).search("from linkedin", 30)!!
        assertEquals(listOf("job"), linkedin.hits.map { it.id })
    }

    @Test
    fun `a parse that fails or dawdles costs only the filters`() = runTest {
        val failed = search(parse = { error("offline") }).search("x", 30)!!
        assertNull(failed.interpretation)
        assertEquals(4, failed.hits.size)
        val slow = search(parse = { delay(10_000); Interpretation(types = listOf("PDF")) }).search("x", 30)!!
        assertEquals(4, slow.hits.size)
    }

    @Test
    fun `without the model there is no answer, so the caller falls back`() = runTest {
        assertNull(search(model = false).search("x", 30))
    }
}
