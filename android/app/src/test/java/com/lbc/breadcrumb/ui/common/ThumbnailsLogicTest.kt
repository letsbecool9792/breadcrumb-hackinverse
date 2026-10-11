package com.lbc.breadcrumb.ui.common

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for thumbnail logic and sampleSize calculation (docs/design.md §3).
 */
class ThumbnailsLogicTest {

    @Test
    fun sampleSizeKeepsBothSidesAtLeastTargetPx() {
        // raw: 4000x3000, targetPx: 360
        // sample 1: 2000, 1500 >= 360 -> 2
        // sample 2: 1000, 750 >= 360 -> 4
        // sample 4: 500, 375 >= 360 -> 8
        // sample 8: 250, 187 < 360 -> stop at 8
        val sample = Thumbnails.sampleSize(4000, 3000, 360)
        assertEquals(8, sample)

        // Both sides after downsample are >= 360
        assertTrue(4000 / sample >= 360)
        assertTrue(3000 / sample >= 360)

        // Next power of two would violate >= 360 on height
        assertTrue(3000 / (sample * 2) < 360)
    }

    @Test
    fun sampleSizeIsOneWhenEitherSideIsSmallerThanTargetPx() {
        // Width is 500, height is 300 (< 360). Downsampling by 2 would make height 150 < 360.
        val sample = Thumbnails.sampleSize(500, 300, 360)
        assertEquals(1, sample)
    }

    @Test
    fun sampleSizeIsPowerOfTwo() {
        val testCases = listOf(
            Triple(1440, 1440, 360) to 4, // 1440/4 = 360 >= 360
            Triple(1080, 720, 360) to 2,  // 720/2 = 360 >= 360, 720/4 = 180 < 360
            Triple(1000, 800, 140) to 4,  // 800/4 = 200 >= 140, 800/8 = 100 < 140
            Triple(360, 360, 360) to 1,
            Triple(200, 200, 360) to 1,
        )

        for ((input, expectedSample) in testCases) {
            val (w, h, target) = input
            val calculated = Thumbnails.sampleSize(w, h, target)
            assertEquals("sampleSize for ${w}x$h at target $target", expectedSample, calculated)
            // Ensure calculated sample is a power of two
            assertEquals(0, calculated and (calculated - 1))
        }
    }

    @Test
    fun cacheKeyMatchesDesignSpecification() {
        val memory = Memory(
            id = "test-memory-id",
            type = MemoryType.IMAGE,
            localUri = "file:///data/user/0/app/files/originals/test.jpg",
        )

        val key = Thumbnails.key(memory, 360)
        assertEquals("test-memory-id@360:file:///data/user/0/app/files/originals/test.jpg", key)
    }
}
