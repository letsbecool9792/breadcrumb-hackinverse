package com.lbc.breadcrumb.ui.home

/** The numbers behind [SheetLayer]'s drag, kept pure so the JVM can test them (design §10). */
internal object SheetMath {

    /** Dragged past this share of its height, a sheet is dismissed on release. */
    const val DISMISS_FRACTION = 0.22f

    /** Flung down faster than this (px/s), a sheet is dismissed on release. */
    const val DISMISS_VELOCITY = 1_600f

    /** A back swipe at full progress moves the sheet this share of its height. */
    const val BACK_TRAVEL = 0.3f

    /** The scrim's alpha when the sheet is at home. */
    const val SCRIM_ALPHA = 0.6f

    /** The sheet is pulled down by [drag] of [height] px and released at [velocity] px/s downwards. */
    fun shouldDismiss(drag: Float, height: Float, velocity: Float): Boolean =
        height > 0f && drag > 0f && (drag > height * DISMISS_FRACTION || velocity > DISMISS_VELOCITY)

    /** Past the point where letting go would dismiss: where the haptic fires. */
    fun pastDismissPoint(drag: Float, height: Float): Boolean = height > 0f && drag > height * DISMISS_FRACTION

    /** The scrim lightens as the sheet is pulled: 1 at home, 0.2 with the sheet pulled out of sight. */
    fun scrimLightening(drag: Float, height: Float): Float {
        if (height <= 0f) return 1f
        return 1f - 0.8f * (drag / height).coerceIn(0f, 1f)
    }

    /** How far a back swipe at [progress] (0 to 1) moves the sheet. */
    fun backDrag(height: Float, progress: Float): Float = height * BACK_TRAVEL * progress.coerceIn(0f, 1f)
}
