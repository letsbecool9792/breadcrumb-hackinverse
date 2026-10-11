package com.lbc.breadcrumb.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit test for [stepLight] (docs/design.md §2, docs/workstreams/reading.md R6).
 *
 * Requirements:
 * - Each crumb peaks in turn.
 * - The last fifth of a lap is rest.
 */
class StepLightTest {

    @Test
    fun eachCrumbPeaksInTurn() {
        // Lap step is 1/5 = 0.2. Peak for crumb i is step * i + step / 2.
        // Crumb 0 peaks at 0.1
        // Crumb 1 peaks at 0.3
        // Crumb 2 peaks at 0.5
        // Crumb 3 peaks at 0.7
        val peaks = floatArrayOf(0.1f, 0.3f, 0.5f, 0.7f)

        for (i in 0..3) {
            val peakPhase = peaks[i]
            val litAtPeak = stepLight(peakPhase, i)
            assertEquals("Crumb $i should peak with lit = 1.0 at phase $peakPhase", 1f, litAtPeak, 0.001f)

            // When crumb i is at its peak, all other crumbs should be noticeably dimmer or 0
            for (other in 0..3) {
                if (other != i) {
                    val litOther = stepLight(peakPhase, other)
                    assertTrue("Crumb $other should not be at peak when crumb $i peaks", litOther < 1f)
                }
            }
        }
    }

    @Test
    fun peaksOccurInSequentialOrder() {
        // Peaks strictly increase with i
        val peak0 = 0.1f
        val peak1 = 0.3f
        val peak2 = 0.5f
        val peak3 = 0.7f

        assertTrue(peak0 < peak1)
        assertTrue(peak1 < peak2)
        assertTrue(peak2 < peak3)

        assertEquals(1f, stepLight(peak0, 0), 0.001f)
        assertEquals(1f, stepLight(peak1, 1), 0.001f)
        assertEquals(1f, stepLight(peak2, 2), 0.001f)
        assertEquals(1f, stepLight(peak3, 3), 0.001f)
    }

    @Test
    fun lastFifthOfLapIsRest() {
        // The lap has 5 steps of 0.2 width.
        // Step 0: 0.0 to 0.2 (peaks at 0.1 for crumb 0)
        // Step 1: 0.2 to 0.4 (peaks at 0.3 for crumb 1)
        // Step 2: 0.4 to 0.6 (peaks at 0.5 for crumb 2)
        // Step 3: 0.6 to 0.8 (peaks at 0.7 for crumb 3)
        // Step 4: 0.8 to 1.0 (the 5th step is rest)
        // In the rest period (from phase 0.9 to 1.0), all crumbs are fully dark (lit = 0)
        val restPhases = listOf(0.9f, 0.92f, 0.95f, 0.98f, 1.0f)

        for (phase in restPhases) {
            for (i in 0..3) {
                val lit = stepLight(phase, i)
                assertEquals("Crumb $i should be at rest (0.0) at phase $phase", 0f, lit, 0.001f)
            }
        }
    }

    @Test
    fun litIsClampedBetweenZeroAndOne() {
        var phase = 0f
        while (phase <= 1f) {
            for (i in 0..3) {
                val lit = stepLight(phase, i)
                assertTrue("Lit should be >= 0 at phase $phase for crumb $i", lit >= 0f)
                assertTrue("Lit should be <= 1 at phase $phase for crumb $i", lit <= 1f)
            }
            phase += 0.01f
        }
    }
}
