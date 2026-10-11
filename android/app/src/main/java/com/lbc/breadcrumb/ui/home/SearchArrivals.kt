package com.lbc.breadcrumb.ui.home

/**
 * Which result rows arrive, and when (design §9). A row's first appearance in a
 * search waits 40 ms for each place it holds (up to eight) and then arrives; a
 * row that has already arrived in this search (re-ranked, or scrolled back) does
 * not arrive again; a new phrase starts afresh. Held by the view model, so a
 * rotation replays nothing.
 */
internal class SearchArrivals {

    private var phrase: String? = null
    private val arrived = mutableSetOf<String>()

    /** A search for [phrase] is on screen: a new phrase forgets what arrived for the last. */
    fun begin(phrase: String) {
        if (phrase != this.phrase) {
            this.phrase = phrase
            arrived.clear()
        }
    }

    /**
     * How long row [id], at [index], waits before it arrives; null when it has
     * already arrived in this search and so is simply there, and already lit.
     */
    fun delayFor(id: String, index: Int): Long? =
        if (arrived.add(id)) STAGGER_MILLIS * minOf(index, STAGGER_LIMIT) else null

    companion object {
        const val STAGGER_MILLIS = 40L
        const val STAGGER_LIMIT = 8
        const val ARRIVE_MILLIS = 340

        /** After a row lands, its matched word waits this long, then warms over [WARM_MILLIS]. */
        const val LIGHT_AFTER_MILLIS = 180L
        const val WARM_MILLIS = 520
    }
}
