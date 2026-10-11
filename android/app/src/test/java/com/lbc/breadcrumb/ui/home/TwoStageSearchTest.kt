package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.net.Interpretation
import com.lbc.breadcrumb.net.Ranks
import com.lbc.breadcrumb.net.SearchHit
import com.lbc.breadcrumb.net.SearchOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwoStageSearchTest {

    private val localMem1 = Memory(id = "m1", type = MemoryType.TEXT, rawText = "qualcomm local match")
    private val localMem2 = Memory(id = "m2", type = MemoryType.IMAGE, summary = "second match")

    @Test
    fun `an empty query gives Resting`() = runTest {
        val queries = MutableSharedFlow<String>()
        val emitted = mutableListOf<SearchState>()

        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            twoStageSearch(
                queries = queries,
                wordSearch = { listOf(localMem1) },
                serverSearch = { SearchOutcome.Found(emptyList(), null) },
                heldMemories = { listOf(localMem1) },
            ).collect { emitted.add(it) }
        }

        queries.emit("")
        runCurrent()

        assertEquals(listOf(SearchState.Resting), emitted)
    }

    @Test
    fun `word search answers at once as RANKING and server answers after 450 ms as RANKED`() = runTest {
        val queries = MutableSharedFlow<String>()
        val emitted = mutableListOf<SearchState>()

        val serverHit = SearchHit(id = "m2", score = 0.033, ranks = Ranks(vector = 1, text = 1))
        val interpretation = Interpretation(query = "qualcomm")

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            twoStageSearch(
                queries = queries,
                wordSearch = { listOf(localMem1, localMem2) },
                serverSearch = { SearchOutcome.Found(listOf(serverHit), interpretation) },
                heldMemories = { listOf(localMem1, localMem2) },
            ).collect { emitted.add(it) }
        }

        queries.emit("qualcomm")
        runCurrent()

        // At t = 0 ms: word search answered, server has not yet answered
        assertEquals(1, emitted.size)
        val stage1 = emitted[0] as SearchState.Searching
        assertEquals("qualcomm", stage1.phrase)
        assertEquals(SearchStatus.RANKING, stage1.status)
        assertEquals(null, stage1.interpretation)
        assertEquals(2, stage1.results.size)
        assertEquals("m1", stage1.results[0].memory.id)
        assertEquals(null, stage1.results[0].hit)

        // Advance 449 ms: still waiting
        advanceTimeBy(449)
        runCurrent()
        assertEquals(1, emitted.size)

        // Advance past 450 ms: server answers
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, emitted.size)
        val stage2 = emitted[1] as SearchState.Searching
        assertEquals("qualcomm", stage2.phrase)
        assertEquals(SearchStatus.RANKED, stage2.status)
        assertEquals(interpretation, stage2.interpretation)
        assertEquals(1, stage2.results.size)
        assertEquals("m2", stage2.results[0].memory.id)
        assertEquals(serverHit, stage2.results[0].hit)
    }

    @Test
    fun `unavailable server leaves word matches standing as OFFLINE or UNAVAILABLE`() = runTest {
        val queries = MutableSharedFlow<String>()
        val emitted = mutableListOf<SearchState>()

        var isOffline = true

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            twoStageSearch(
                queries = queries,
                wordSearch = { listOf(localMem1) },
                serverSearch = {
                    if (isOffline) {
                        SearchOutcome.Unavailable("Refused", offline = true)
                    } else {
                        SearchOutcome.Unavailable("HTTP 503", offline = false)
                    }
                },
                heldMemories = { listOf(localMem1) },
            ).collect { emitted.add(it) }
        }

        // 1. Offline scenario
        queries.emit("test")
        runCurrent()
        advanceTimeBy(450)
        runCurrent()

        assertEquals(2, emitted.size)
        val offlineState = emitted[1] as SearchState.Searching
        assertEquals(SearchStatus.OFFLINE, offlineState.status)
        assertEquals(listOf(SearchResult(localMem1, null)), offlineState.results)

        // 2. Unavailable scenario (non-offline error like 503)
        isOffline = false
        queries.emit("another")
        runCurrent()
        advanceTimeBy(450)
        runCurrent()

        assertEquals(4, emitted.size)
        val busyState = emitted[3] as SearchState.Searching
        assertEquals(SearchStatus.UNAVAILABLE, busyState.status)
        assertEquals(listOf(SearchResult(localMem1, null)), busyState.results)
    }

    @Test
    fun `a keystroke inside the pause abandons the server call`() = runTest {
        val queries = MutableSharedFlow<String>()
        val emitted = mutableListOf<SearchState>()
        var serverCalls = 0

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            twoStageSearch(
                queries = queries,
                wordSearch = { listOf(localMem1) },
                serverSearch = {
                    serverCalls++
                    SearchOutcome.Found(emptyList(), null)
                },
                heldMemories = { listOf(localMem1) },
            ).collect { emitted.add(it) }
        }

        queries.emit("intern")
        runCurrent()
        advanceTimeBy(200) // Keystroke at 200 ms (before 450 ms pause)

        queries.emit("internship")
        runCurrent()

        // Advance 300 ms (now 500 ms from start, but only 300 ms after "internship")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(0, serverCalls)

        // Advance the remaining 150 ms to hit the 450 ms mark for "internship"
        advanceTimeBy(150)
        runCurrent()
        assertEquals(1, serverCalls)
        val lastState = emitted.last() as SearchState.Searching
        assertEquals("internship", lastState.phrase)
        assertEquals(SearchStatus.RANKED, lastState.status)
    }

    @Test
    fun `the same phrase with different spacing starts nothing new`() = runTest {
        val queries = MutableSharedFlow<String>()
        val emitted = mutableListOf<SearchState>()
        var wordSearchCalls = 0

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            twoStageSearch(
                queries = queries,
                wordSearch = {
                    wordSearchCalls++
                    listOf(localMem1)
                },
                serverSearch = { SearchOutcome.Found(emptyList(), null) },
                heldMemories = { listOf(localMem1) },
            ).collect { emitted.add(it) }
        }

        queries.emit("  qualcomm   internship  ")
        runCurrent()
        advanceTimeBy(450)
        runCurrent()

        assertEquals(1, wordSearchCalls)
        val countAfterFirst = emitted.size

        // Emit equivalent query with different spacing
        queries.emit("qualcomm internship")
        runCurrent()
        advanceTimeBy(450)
        runCurrent()

        assertEquals(1, wordSearchCalls)
        assertEquals(countAfterFirst, emitted.size)
    }
}
