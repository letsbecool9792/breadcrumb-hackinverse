package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UndoableDeletesTest {

    // Eager, so each step has finished by the time the call returns.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val log = mutableListOf<String>()
    private val restored = mutableListOf<Memory>()

    /** One gate per undo window: opening it is five seconds passing. */
    private val windows = mutableListOf<CompletableDeferred<Unit>>()

    private val deletes = UndoableDeletes(
        scope = scope,
        deleteRow = { log += "row gone ${it.id}" },
        restoreRow = { log += "row back ${it.id}"; restored += it },
        deleteOriginal = { log += "file gone ${it.id}" },
        scheduleWork = { log += "upload" },
        now = { 9_000L },
        undoWindow = { CompletableDeferred<Unit>().also { windows += it }.await() },
    )

    @After
    fun stop() = scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()

    private fun memory(id: String) = Memory(
        id = id,
        type = MemoryType.IMAGE,
        syncState = SyncState.SYNCED,
        imageSentAt = 5_000,
        enrichedAt = 6_000,
        summary = "A job posting",
    )

    @Test
    fun `a delete takes the row at once and leaves the file and the server alone`() {
        deletes.delete(memory("a"))

        assertEquals(listOf("row gone a"), log)
        assertEquals("a", deletes.pending.value?.id)
    }

    @Test
    fun `five seconds on, the delete is final`() {
        deletes.delete(memory("a"))

        windows.single().complete(Unit)

        assertEquals(listOf("row gone a", "file gone a", "upload"), log)
        assertNull(deletes.pending.value)
    }

    @Test
    fun `undo brings the row back as pending, to be sent again from scratch`() {
        deletes.delete(memory("a"))

        deletes.undo()

        assertEquals(listOf("row gone a", "row back a", "upload"), log)
        val back = restored.single()
        assertEquals(SyncState.PENDING, back.syncState)
        // The server may have heard the delete: its copy of the picture's reading and summary can be gone.
        assertNull(back.imageSentAt)
        assertNull(back.enrichedAt)
        assertEquals(9_000L, back.updatedAt)
        assertEquals("A job posting", back.summary)
        assertNull(deletes.pending.value)
    }

    @Test
    fun `an undone delete is not made final when its window would have ended`() {
        deletes.delete(memory("a"))
        deletes.undo()

        windows.single().complete(Unit)

        assertEquals(listOf("row gone a", "row back a", "upload"), log)
    }

    @Test
    fun `a second delete makes the first final`() {
        deletes.delete(memory("a"))

        deletes.delete(memory("b"))

        assertEquals(listOf("row gone a", "file gone a", "upload", "row gone b"), log)
        assertEquals("b", deletes.pending.value?.id)
        // The first one's window ending later changes nothing.
        windows.first().complete(Unit)
        assertEquals("b", deletes.pending.value?.id)
        assertEquals(4, log.size)
    }

    @Test
    fun `only the latest delete can be undone`() {
        deletes.delete(memory("a"))
        deletes.delete(memory("b"))

        deletes.undo()

        assertEquals("row back b", log.last { it.startsWith("row back") })
        assertEquals(0, log.count { it == "row back a" })
    }

    @Test
    fun `finishing mid-undo makes the delete final, and its window then does nothing`() {
        deletes.delete(memory("a"))

        deletes.finish()
        windows.single().complete(Unit)

        assertEquals(listOf("row gone a", "file gone a", "upload"), log)
        assertNull(deletes.pending.value)
    }

    @Test
    fun `finishing with nothing pending does nothing`() {
        deletes.finish()
        deletes.undo()

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `changes happen in the order asked, so a restore never overtakes its delete`() {
        deletes.delete(memory("a"))
        deletes.undo()
        deletes.delete(memory("a"))
        windows.last().complete(Unit)

        assertEquals(
            listOf("row gone a", "row back a", "upload", "row gone a", "file gone a", "upload"),
            log,
        )
    }

    @Test
    fun `a change that fails does not stop the ones behind it`() {
        val failing = UndoableDeletes(
            scope = scope,
            deleteRow = { error("database busy") },
            restoreRow = { log += "row back ${it.id}" },
            deleteOriginal = {},
            scheduleWork = { log += "upload" },
            undoWindow = { CompletableDeferred<Unit>().await() },
        )

        failing.delete(memory("a"))
        failing.undo()

        assertEquals(listOf("row back a", "upload"), log)
    }
}
