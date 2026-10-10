package com.lbc.breadcrumb.ocr

/**
 * How far to downsample a [width] x [height] picture before reading it, as a
 * `BitmapFactory.Options.inSampleSize`.
 *
 * Halves only while the short side stays at least [MIN_SHORT_SIDE]: ML Kit
 * wants about 16 px per character, and a phone screenshot's small text has
 * that only at full width. Sizing by the long side instead would halve an
 * ordinary 1080 x 2400 screenshot and shrink a long scrolling one further.
 * Then halves again while the picture is over [MAX_PIXELS], so a very long
 * screenshot cannot run the phone out of memory.
 */
internal fun sampleSizeFor(width: Int, height: Int): Int {
    var sampleSize = 1
    while (minOf(width, height) / (sampleSize * 2) >= MIN_SHORT_SIDE) {
        sampleSize *= 2
    }
    while (width.toLong() / sampleSize * (height / sampleSize) > MAX_PIXELS) {
        sampleSize *= 2
    }
    return sampleSize
}

/** The short side a downsampled picture keeps: a phone screenshot's full width. */
private const val MIN_SHORT_SIDE = 1080

/** The most pixels decoded at once: about 48 MB as ARGB_8888. */
private const val MAX_PIXELS = 12_000_000L
