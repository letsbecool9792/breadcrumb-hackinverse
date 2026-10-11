package com.lbc.breadcrumb.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.lbc.breadcrumb.ui.theme.Ink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val SheetEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val ENTER_MILLIS = 420
private const val EXIT_MILLIS = 300

/**
 * The app's own sheet (design §10). Drawn in the screen's own composition,
 * unlike Material's sheet, so a picture can travel from its tile or row into
 * it and back (see [TravellingPicture]). It gives back what Material's gave:
 * a height limit, a scrim that closes it, drag-to-dismiss handed over from the
 * content's scroll, predictive Back, and standing on the keyboard.
 *
 * [item] is what the sheet shows, null for closed. The item being closed stays
 * drawn while the sheet leaves. [content] gets the item and the sheet's
 * [AnimatedVisibilityScope], which the picture's shared element needs.
 */
@Composable
fun <T : Any> SheetLayer(
    item: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxFraction: Float = 0.86f,
    content: @Composable ColumnScope.(T, AnimatedVisibilityScope) -> Unit,
) {
    // What was last shown, so it stays drawn while the sheet slides away. Not
    // state: a change of [item] already recomposes this.
    val last = remember { LastShown<T>() }
    if (item != null) last.value = item
    val shown = item ?: last.value

    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val pull = remember { SheetPull(scope) { dismiss() } }
    val haptic = LocalHapticFeedback.current

    // A sheet opens at home, however the last one left.
    LaunchedEffect(item != null) { if (item != null) pull.reset() }

    // One tick as a pull crosses the point where letting go would dismiss it.
    // Not during a back swipe, which has its own feel.
    LaunchedEffect(pull) {
        snapshotFlow { !pull.backSwiping && SheetMath.pastDismissPoint(pull.drag, pull.height) }
            .distinctUntilChanged()
            .filter { it }
            .collect { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }

    PredictiveBackHandler(enabled = item != null) { progress ->
        pull.backSwiping = true
        try {
            progress.collect { pull.moveTo(SheetMath.backDrag(pull.height, it.progress)) }
            pull.backSwiping = false
            dismiss()
        } catch (cancelled: CancellationException) {
            pull.backSwiping = false
            pull.settleHome()
            throw cancelled
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * maxFraction
        AnimatedVisibility(
            visible = item != null,
            // Its two children enter and leave on their own clocks.
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            val visibility = this
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .animateEnterExit(
                            enter = fadeIn(tween(ENTER_MILLIS)),
                            exit = fadeOut(tween(EXIT_MILLIS)),
                        )
                        .graphicsLayer { alpha = SheetMath.scrimLightening(pull.drag, pull.height) }
                        .background(Color.Black.copy(alpha = SheetMath.SCRIM_ALPHA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                        .testTag("sheet-scrim"),
                )
                // It stands on the keyboard and never slides under the status bar.
                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxSheetHeight)
                            .animateEnterExit(
                                enter = slideInVertically(tween(ENTER_MILLIS, easing = SheetEasing)) { it },
                                exit = slideOutVertically(tween(EXIT_MILLIS, easing = SheetEasing)) { it },
                            )
                            .draggable(
                                state = rememberDraggableState { pull.dragBy(it) },
                                orientation = Orientation.Vertical,
                                onDragStopped = { pull.release(it) },
                            )
                            .nestedScroll(remember(pull) { PullFromContent(pull) })
                            .graphicsLayer { translationY = pull.drag }
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(Ink)
                            // Taps on the sheet are its own, not the scrim's.
                            .pointerInput(Unit) { detectTapGestures { } }
                            .testTag("sheet")
                            .onSizeChanged { pull.height = it.height.toFloat() },
                    ) {
                        shown?.let { content(it, visibility) }
                    }
                }
            }
        }
    }
}

private class LastShown<T> {
    var value: T? = null
}

/**
 * The sheet's drag: where it is pulled to, and what happens on letting go.
 * Read while drawing, so a pull never recomposes anything.
 */
@Stable
internal class SheetPull(private val scope: CoroutineScope, private val onDismiss: () -> Unit) {
    var drag by mutableFloatStateOf(0f)
        private set
    var height by mutableFloatStateOf(0f)
    var backSwiping = false

    private var settling: Job? = null

    fun reset() {
        settling?.cancel()
        drag = 0f
    }

    fun moveTo(to: Float) {
        settling?.cancel()
        drag = to.coerceAtLeast(0f)
    }

    fun dragBy(delta: Float) = moveTo(drag + delta)

    /** Let go at [velocity] px/s downwards: dismissed, or back home with a spring. */
    fun release(velocity: Float) {
        if (SheetMath.shouldDismiss(drag, height, velocity)) onDismiss() else settleHome()
    }

    fun settleHome() {
        settling?.cancel()
        settling = scope.launch {
            animate(drag, 0f, animationSpec = spring(stiffness = 500f)) { value, _ -> drag = value }
        }
    }
}

/**
 * Hands the content's scroll to the sheet. Content already at its top, pulled
 * further down, moves the sheet; pushed back up, it brings the sheet home
 * before it scrolls; and a fling that starts with the sheet away is the
 * sheet's to release.
 */
private class PullFromContent(private val pull: SheetPull) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || pull.drag <= 0f) return Offset.Zero
        val taken = maxOf(available.y, -pull.drag)
        pull.dragBy(taken)
        return Offset(0f, taken)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        pull.dragBy(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pull.drag <= 0f) return Velocity.Zero
        pull.release(available.y)
        return available
    }
}
