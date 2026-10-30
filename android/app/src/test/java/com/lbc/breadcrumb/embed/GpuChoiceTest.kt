package com.lbc.breadcrumb.embed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuChoiceTest {

    private var stored: String? = null
    private val choice = GpuChoice(read = { stored }, write = { stored = it })

    @Test
    fun `untried, the gpu is tried, and once it works it stays`() {
        assertTrue(choice.allowed())
        choice.starting()
        choice.worked()
        assertTrue(choice.allowed())
    }

    @Test
    fun `a start that throws leaves the cpu for good`() {
        choice.starting()
        choice.failed()
        assertFalse(choice.allowed())
        assertFalse(choice.allowed())
    }

    @Test
    fun `a start that never ended, a crash, counts as failing`() {
        choice.starting()
        // The process died here; the next one asks.
        assertFalse(GpuChoice(read = { stored }, write = { stored = it }).allowed())
        assertEquals("fails", stored)
    }
}
