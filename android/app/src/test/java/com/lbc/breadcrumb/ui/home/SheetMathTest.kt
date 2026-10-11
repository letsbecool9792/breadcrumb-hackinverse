package com.lbc.breadcrumb.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetMathTest {

    private val height = 1_000f

    @Test
    fun `a pull past 22 percent of the sheet dismisses it on release`() {
        assertTrue(SheetMath.shouldDismiss(drag = 221f, height = height, velocity = 0f))
        assertFalse(SheetMath.shouldDismiss(drag = 220f, height = height, velocity = 0f))
    }

    @Test
    fun `a fling down faster than 1,600 px a second dismisses it`() {
        assertTrue(SheetMath.shouldDismiss(drag = 40f, height = height, velocity = 1_601f))
        assertFalse(SheetMath.shouldDismiss(drag = 40f, height = height, velocity = 1_600f))
    }

    @Test
    fun `a fling up never dismisses`() {
        assertFalse(SheetMath.shouldDismiss(drag = 40f, height = height, velocity = -3_000f))
    }

    @Test
    fun `a sheet at home is not dismissed by a fling`() {
        assertFalse(SheetMath.shouldDismiss(drag = 0f, height = height, velocity = 5_000f))
    }

    @Test
    fun `an unmeasured sheet is never dismissed`() {
        assertFalse(SheetMath.shouldDismiss(drag = 500f, height = 0f, velocity = 5_000f))
    }

    @Test
    fun `the haptic point is the dismiss point`() {
        assertTrue(SheetMath.pastDismissPoint(221f, height))
        assertFalse(SheetMath.pastDismissPoint(220f, height))
        assertFalse(SheetMath.pastDismissPoint(500f, 0f))
    }

    @Test
    fun `the scrim lightens as the sheet is pulled away`() {
        assertEquals(1f, SheetMath.scrimLightening(0f, height), 0.0001f)
        assertEquals(0.6f, SheetMath.scrimLightening(500f, height), 0.0001f)
        assertEquals(0.2f, SheetMath.scrimLightening(1_000f, height), 0.0001f)
        // Pulled beyond the sheet's height, and unmeasured, it stays within range.
        assertEquals(0.2f, SheetMath.scrimLightening(2_000f, height), 0.0001f)
        assertEquals(1f, SheetMath.scrimLightening(300f, 0f), 0.0001f)
    }

    @Test
    fun `a back swipe moves the sheet 30 percent of its height at most`() {
        assertEquals(0f, SheetMath.backDrag(height, 0f), 0.0001f)
        assertEquals(150f, SheetMath.backDrag(height, 0.5f), 0.0001f)
        assertEquals(300f, SheetMath.backDrag(height, 1f), 0.0001f)
        assertEquals(300f, SheetMath.backDrag(height, 1.4f), 0.0001f)
    }
}
