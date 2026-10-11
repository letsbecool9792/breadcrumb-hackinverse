package com.lbc.breadcrumb.ui.home

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** Pure numbers behind the mosaic's tiles and its empty state (design §6, §7). */
internal object MosaicMath {

    /** A picture tile's height before its picture has loaded. */
    const val PICTURE_DEFAULT_DP = 190f
    const val PICTURE_MIN_DP = 150f
    const val PICTURE_MAX_DP = 270f

    /**
     * A picture tile's height: its width times the picture's aspect (height over
     * width), kept between 150 and 270 dp, so a long screenshot does not run
     * down the page. 190 dp until the picture has loaded.
     */
    fun pictureTileHeightDp(widthDp: Float, aspect: Float?): Float =
        if (aspect == null || aspect <= 0f) PICTURE_DEFAULT_DP
        else (widthDp * aspect).coerceIn(PICTURE_MIN_DP, PICTURE_MAX_DP)

    /** Crumbs in the empty mosaic's trail. */
    const val TRAIL_CRUMBS = 7

    /**
     * How lit crumb [i] of the trail is at lap [phase] in [0, 1]: each peaks in
     * turn over an eighth of the lap, and the eighth step is rest.
     */
    fun trailLight(phase: Float, i: Int): Float {
        val step = 1f / 8f
        val peak = step * i + step / 2f
        return (1f - abs(phase - peak) / step).coerceIn(0f, 1f)
    }

    /**
     * Where crumb [i] sits on the quadratic curve from 26% of the width and 46%
     * of the height, bending through 34% and 88%, to the "+" at the bottom right
     * ([endInsetX] in from the right, [endInsetY] up from the bottom).
     */
    fun trailPoint(i: Int, width: Float, height: Float, endInsetX: Float, endInsetY: Float): Offset {
        val t = i / (TRAIL_CRUMBS - 1).toFloat()
        val p0 = Offset(width * 0.26f, height * 0.46f)
        val p1 = Offset(width * 0.34f, height * 0.88f)
        val p2 = Offset(width - endInsetX, height - endInsetY)
        val a = (1f - t) * (1f - t)
        val b = 2f * (1f - t) * t
        val c = t * t
        return Offset(a * p0.x + b * p1.x + c * p2.x, a * p0.y + b * p1.y + c * p2.y)
    }
}
