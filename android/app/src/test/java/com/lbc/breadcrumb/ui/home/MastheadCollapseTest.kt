package com.lbc.breadcrumb.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MastheadCollapseTest {

    private val height = 200

    @Test
    fun `the masthead in view has not collapsed`() {
        assertEquals(0f, mastheadCollapse(0, 0, height), 0f)
        assertEquals(0f, mastheadCollapse(0, 40, height), 0f)
    }

    @Test
    fun `it holds until the masthead is scrolled nearly half away`() {
        assertEquals(0f, mastheadCollapse(0, 90, height), 0f)
    }

    @Test
    fun `it rises over the next stretch`() {
        // Halfway between 45% (90 px) and 90% (180 px).
        assertEquals(0.5f, mastheadCollapse(0, 135, height), 0.001f)
    }

    @Test
    fun `it is complete once most of the masthead has gone`() {
        assertEquals(1f, mastheadCollapse(0, 180, height), 0f)
        assertEquals(1f, mastheadCollapse(0, 199, height), 0f)
    }

    @Test
    fun `scrolled past the masthead it stays collapsed`() {
        assertEquals(1f, mastheadCollapse(1, 0, height), 0f)
        assertEquals(1f, mastheadCollapse(7, 30, height), 0f)
    }

    @Test
    fun `before the masthead is measured nothing collapses`() {
        assertEquals(0f, mastheadCollapse(0, 50, 0), 0f)
    }
}
