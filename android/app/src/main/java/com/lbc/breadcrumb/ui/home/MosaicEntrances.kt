package com.lbc.breadcrumb.ui.home

/** How a tile enters the mosaic the first time it is drawn (design §6). */
internal sealed interface Entrance {
    /** Already shown: nothing happens. */
    data object None : Entrance

    /** Rises into place after [delayMillis]: the tiles the app opened on, and any a scroll reaches. */
    data class Rise(val delayMillis: Int) : Entrance

    /** Drops in with the crumb's bounce: a memory that arrived while the mosaic was up. */
    data object Drop : Entrance
}

/**
 * Decides each tile's entrance. Held by the screen, so a search and back, or a
 * rotation, replays nothing.
 *
 * - The first list the mosaic is given is what the app opened on. Within
 *   [OPENING_WINDOW_MILLIS] of opening, its tiles rise one after another; a tile
 *   a scroll reaches later rises with no wait.
 * - A memory in a later list that was not in the one before drops in: a new
 *   save, or an Undo.
 * - A tile already shown does nothing.
 * - A memory that leaves the list is forgotten, so it drops in if it comes back.
 */
internal class MosaicEntrances(private val now: () -> Long = System::currentTimeMillis) {

    private var openedAt: Long? = null
    private val opening = mutableSetOf<String>()
    private val arrivals = mutableSetOf<String>()
    private val known = mutableSetOf<String>()
    private val shown = mutableSetOf<String>()

    /** The list the mosaic now draws, newest first. Call it on every change. */
    fun submit(ids: List<String>) {
        val current = ids.toSet()
        if (openedAt == null) {
            openedAt = now()
            opening += current
        } else {
            arrivals += current - known
        }
        // What left is forgotten, wherever it was remembered.
        (known - current).forEach { gone ->
            opening -= gone
            arrivals -= gone
            shown -= gone
        }
        known.clear()
        known += current
    }

    /** The entrance for [id], drawn at [index] in the list, the first time it is drawn. */
    fun entranceFor(id: String, index: Int): Entrance {
        if (!shown.add(id)) return Entrance.None
        if (id in arrivals) return Entrance.Drop
        val opened = openedAt
        val inOpeningWindow = id in opening && opened != null && now() - opened < OPENING_WINDOW_MILLIS
        return Entrance.Rise(if (inOpeningWindow) STAGGER_MILLIS * minOf(index, STAGGER_LIMIT) else 0)
    }

    companion object {
        const val OPENING_WINDOW_MILLIS = 1_500L
        const val STAGGER_MILLIS = 45
        const val STAGGER_LIMIT = 10
        const val RISE_MILLIS = 440
        const val DROP_MILLIS = 640
    }
}
