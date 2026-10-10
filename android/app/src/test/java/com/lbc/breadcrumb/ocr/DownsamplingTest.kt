package com.lbc.breadcrumb.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class DownsamplingTest {

    @Test
    fun `a phone screenshot is read at full size`() {
        assertEquals(1, sampleSizeFor(1080, 2400))
        assertEquals(1, sampleSizeFor(1220, 2712))
        assertEquals(1, sampleSizeFor(1440, 3200))
    }

    @Test
    fun `a long scrolling screenshot keeps its width until it is too big to hold`() {
        assertEquals(1, sampleSizeFor(1080, 8000))
        assertEquals(2, sampleSizeFor(1080, 20000))
    }

    @Test
    fun `a camera photo is halved only while its short side stays readable`() {
        assertEquals(2, sampleSizeFor(4000, 3000))
        assertEquals(4, sampleSizeFor(8000, 6000))
        assertEquals(2, sampleSizeFor(12000, 3000))
    }

    @Test
    fun `a small picture is left alone`() {
        assertEquals(1, sampleSizeFor(100, 100))
        assertEquals(1, sampleSizeFor(600, 300))
    }
}
