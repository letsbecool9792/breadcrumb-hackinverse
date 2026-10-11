package com.lbc.breadcrumb.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Glyphs as drawn on the phone: the plus and the clear × run across their
 * whole box (design §2), which a button of 18dp or 9dp depends on.
 */
@RunWith(AndroidJUnit4::class)
class GlyphsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun draw(glyph: @Composable (Modifier) -> Unit): Pair<PixelMap, Float> {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(40.dp).background(Color.Black).testTag("glyph")) {
                glyph(Modifier.size(40.dp))
            }
        }
        val pixels = compose.onNodeWithTag("glyph").captureToImage().toPixelMap()
        return pixels to density
    }

    private fun painted(pixels: PixelMap, x: Int, y: Int): Boolean {
        val c = pixels[x, y]
        return c.red > 0.5f && c.green > 0.5f && c.blue > 0.5f
    }

    @Test
    fun theTwoLinesOfAPlusRunAcrossTheWholeBox() {
        val (pixels, density) = draw { PlusGlyph(it, Color.White, strokeWidth = 4.dp) }
        val w = pixels.width
        val mid = w / 2
        val near = (3 * density).toInt() // inside the line's round cap, a dp from the box's edge

        assertTrue("left end", painted(pixels, near, mid))
        assertTrue("right end", painted(pixels, w - 1 - near, mid))
        assertTrue("top end", painted(pixels, mid, near))
        assertTrue("bottom end", painted(pixels, mid, w - 1 - near))
        assertTrue("centre", painted(pixels, mid, mid))
        // Nothing in the corners: it is a plus, not a box.
        assertEquals(false, painted(pixels, near, near))
        assertEquals(false, painted(pixels, w - 1 - near, w - 1 - near))
    }

    @Test
    fun theTwoDiagonalsOfAClearCrossRunCornerToCorner() {
        val (pixels, density) = draw { ClearGlyph(it, Color.White, strokeWidth = 4.dp) }
        val w = pixels.width
        val near = (3 * density).toInt()

        assertTrue("top left", painted(pixels, near, near))
        assertTrue("bottom right", painted(pixels, w - 1 - near, w - 1 - near))
        assertTrue("top right", painted(pixels, w - 1 - near, near))
        assertTrue("bottom left", painted(pixels, near, w - 1 - near))
        assertTrue("centre", painted(pixels, w / 2, w / 2))
        // Not along the middle lines: it is a cross, not a plus.
        assertEquals(false, painted(pixels, near, w / 2))
        assertEquals(false, painted(pixels, w / 2, near))
    }
}
