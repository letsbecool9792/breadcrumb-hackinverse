package com.lbc.breadcrumb.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportLineTest {

    @Test
    fun `null progress gives null line`() {
        assertNull(importLine(null))
    }

    @Test
    fun `scanning progress gives looking with unicode ellipsis`() {
        assertEquals("looking…", importLine(ImportProgress.Scanning("Scanning folder...")))
    }

    @Test
    fun `running progress gives bringing in current of total`() {
        assertEquals(
            "bringing in · 1 of 10",
            importLine(ImportProgress.Running(current = 1, total = 10, imported = 0, skipped = 0, currentItem = "photo.jpg")),
        )
        assertEquals(
            "bringing in · 42 of 100",
            importLine(ImportProgress.Running(current = 42, total = 100, imported = 30, skipped = 11, currentItem = "document.pdf")),
        )
    }

    @Test
    fun `done with total zero or zero imported and zero skipped gives nothing to bring in`() {
        assertEquals(
            "nothing to bring in",
            importLine(ImportProgress.Done(total = 0, imported = 0, skipped = 0)),
        )
        assertEquals(
            "nothing to bring in",
            importLine(ImportProgress.Done(total = 5, imported = 0, skipped = 0)),
        )
    }

    @Test
    fun `done with nothing imported gives nothing new and count already here`() {
        assertEquals(
            "nothing new · 1 already here",
            importLine(ImportProgress.Done(total = 1, imported = 0, skipped = 1)),
        )
        assertEquals(
            "nothing new · 15 already here",
            importLine(ImportProgress.Done(total = 15, imported = 0, skipped = 15)),
        )
    }

    @Test
    fun `done with imported and skipped gives brought in and count already here`() {
        assertEquals(
            "brought in 3 · 2 already here",
            importLine(ImportProgress.Done(total = 5, imported = 3, skipped = 2)),
        )
        assertEquals(
            "brought in 1 · 10 already here",
            importLine(ImportProgress.Done(total = 11, imported = 1, skipped = 10)),
        )
    }

    @Test
    fun `done with imported and none skipped gives brought in count only`() {
        assertEquals(
            "brought in 1",
            importLine(ImportProgress.Done(total = 1, imported = 1, skipped = 0)),
        )
        assertEquals(
            "brought in 42",
            importLine(ImportProgress.Done(total = 42, imported = 42, skipped = 0)),
        )
    }

    @Test
    fun `failed progress gives import stopped and reason`() {
        assertEquals(
            "import stopped · permission denied",
            importLine(ImportProgress.Failed("permission denied")),
        )
        assertEquals(
            "import stopped · out of space",
            importLine(ImportProgress.Failed("out of space")),
        )
    }
}
