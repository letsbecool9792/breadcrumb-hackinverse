package com.lbc.breadcrumb.ui.home

/**
 * How far the masthead has handed over to the slim strip, 0 to 1 (design §5).
 *
 * 0 while the masthead is in view, rising only once it is scrolled nearly half
 * away, to 1 when 90% of it has gone; 1 whenever the grid's first visible item
 * is anything after it. [firstVisibleOffset] is how far the first visible item
 * is scrolled up, in pixels.
 */
internal fun mastheadCollapse(firstVisibleIndex: Int, firstVisibleOffset: Int, mastheadHeight: Int): Float {
    if (firstVisibleIndex > 0) return 1f
    if (mastheadHeight <= 0) return 0f
    val scrolled = firstVisibleOffset.toFloat() / mastheadHeight
    return ((scrolled - 0.45f) / 0.45f).coerceIn(0f, 1f)
}
