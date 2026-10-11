package com.lbc.breadcrumb.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicMathTest {

    @Test
    fun `a picture tile is 190 dp until its picture has loaded`() {
        assertEquals(190f, MosaicMath.pictureTileHeightDp(170f, null), 0f)
        assertEquals(190f, MosaicMath.pictureTileHeightDp(170f, 0f), 0f)
    }

    @Test
    fun `a picture tile is as tall as its picture is, within 150 and 270`() {
        assertEquals(170f, MosaicMath.pictureTileHeightDp(170f, 1f), 0.001f)
        assertEquals(255f, MosaicMath.pictureTileHeightDp(170f, 1.5f), 0.001f)
    }

    @Test
    fun `a long screenshot is held to 270 and a wide one to 150`() {
        assertEquals(270f, MosaicMath.pictureTileHeightDp(170f, 2.22f), 0f)
        assertEquals(150f, MosaicMath.pictureTileHeightDp(170f, 0.56f), 0f)
    }

    @Test
    fun `each crumb of the trail peaks in turn`() {
        for (i in 0 until MosaicMath.TRAIL_CRUMBS) {
            val peak = (i + 0.5f) / 8f
            assertEquals("crumb $i at its peak", 1f, MosaicMath.trailLight(peak, i), 0.0001f)
            for (other in 0 until MosaicMath.TRAIL_CRUMBS) {
                if (other != i) assertTrue("crumb $other is not lit at crumb $i's peak", MosaicMath.trailLight(peak, other) < 1f)
            }
        }
    }

    @Test
    fun `the eighth step of the lap is rest`() {
        for (i in 0 until MosaicMath.TRAIL_CRUMBS) {
            assertEquals(0f, MosaicMath.trailLight(0.97f, i), 0.0001f)
        }
    }

    @Test
    fun `the trail starts a quarter in and ends at the plus`() {
        val start = MosaicMath.trailPoint(0, width = 1000f, height = 2000f, endInsetX = 80f, endInsetY = 90f)
        assertEquals(260f, start.x, 0.001f)
        assertEquals(920f, start.y, 0.001f)

        val end = MosaicMath.trailPoint(MosaicMath.TRAIL_CRUMBS - 1, 1000f, 2000f, 80f, 90f)
        assertEquals(920f, end.x, 0.001f)
        assertEquals(1910f, end.y, 0.001f)
    }

    @Test
    fun `the trail walks down and to the right`() {
        val points = (0 until MosaicMath.TRAIL_CRUMBS).map { MosaicMath.trailPoint(it, 1000f, 2000f, 80f, 90f) }
        assertTrue(points.zipWithNext().all { (a, b) -> b.y > a.y })
        assertTrue(points.last().x > points.first().x)
    }
}
