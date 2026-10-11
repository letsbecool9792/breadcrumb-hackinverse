package com.lbc.breadcrumb.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PlusPanelTest {

    @Test
    fun `the plus opens on the composer`() {
        assertEquals(PlusPanel.COMPOSER, PlusPanel.opening(nothingKept = false))
    }

    @Test
    fun `with nothing kept it opens straight on the import panel`() {
        assertEquals(PlusPanel.IMPORT, PlusPanel.opening(nothingKept = true))
    }
}
