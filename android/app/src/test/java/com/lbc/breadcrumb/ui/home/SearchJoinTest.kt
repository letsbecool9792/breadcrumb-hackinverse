package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.net.Ranks
import com.lbc.breadcrumb.net.SearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchJoinTest {

    @Test
    fun `the order is kept, and unknown ids are dropped`() {
        val mem1 = Memory(id = "id-1", type = MemoryType.TEXT, rawText = "first")
        val mem2 = Memory(id = "id-2", type = MemoryType.IMAGE, summary = "second")
        val mem3 = Memory(id = "id-3", type = MemoryType.LINK, title = "third")
        val held = listOf(mem1, mem2, mem3)

        // Server returns hits in order: id-2, id-unknown, id-1
        val hits = listOf(
            SearchHit(id = "id-2", score = 0.033, ranks = Ranks(vector = 1, text = 1)),
            SearchHit(id = "id-unknown", score = 0.020, ranks = Ranks(vector = 2, text = null)),
            SearchHit(id = "id-1", score = 0.016, ranks = Ranks(vector = 3, text = 2)),
        )

        val results = joinHits(hits, held)

        assertEquals(2, results.size)
        assertEquals("id-2", results[0].memory.id)
        assertEquals(hits[0], results[0].hit)

        assertEquals("id-1", results[1].memory.id)
        assertEquals(hits[2], results[1].hit)
    }

    @Test
    fun `empty hits or empty held returns empty list`() {
        val memory = Memory(id = "id-1", type = MemoryType.TEXT)
        assertTrue(joinHits(emptyList(), listOf(memory)).isEmpty())
        assertTrue(joinHits(listOf(SearchHit(id = "id-1")), emptyList()).isEmpty())
    }
}
