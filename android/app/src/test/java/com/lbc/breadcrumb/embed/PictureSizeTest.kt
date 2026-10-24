package com.lbc.breadcrumb.embed

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureSizeTest {

    @Test
    fun `a picture within the long side is sent as it is`() {
        assertEquals(1080 to 1500, Pictures.sizeFor(1080, 1500))
        assertEquals(800 to 600, Pictures.sizeFor(800, 600))
    }

    @Test
    fun `a bigger one is shrunk to a 1,500 px long side, its shape kept`() {
        assertEquals(1500 to 1000, Pictures.sizeFor(6000, 4000))
        assertEquals(675 to 1500, Pictures.sizeFor(1080, 2400))
        // A long scrolling screenshot keeps its shape too.
        assertEquals(162 to 1500, Pictures.sizeFor(1080, 10_000))
    }

    @Test
    fun `decoding halves only while the long side stays at least 1,500 px`() {
        assertEquals(1, Pictures.sampleSizeToFit(1080, 2400))
        assertEquals(2, Pictures.sampleSizeToFit(4000, 3000))
        assertEquals(4, Pictures.sampleSizeToFit(8000, 6000))
        assertEquals(1, Pictures.sampleSizeToFit(800, 600))
    }
}
