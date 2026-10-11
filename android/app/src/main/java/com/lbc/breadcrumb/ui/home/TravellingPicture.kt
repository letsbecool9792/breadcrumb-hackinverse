@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** The scope pictures travel within, or null outside one (previews, tests). */
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The id of the memory whose sheet is open, or null. */
internal val LocalOpenedMemoryId = compositionLocalOf<String?> { null }

private val TravelEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Travel = BoundsTransform { _, _ -> tween(durationMillis = 420, easing = TravelEasing) }

private fun key(memoryId: String) = "picture-$memoryId"

/**
 * One layer over the whole screen, so a picture can travel between its tile
 * or row and the sheet (a shared element only works within one composition).
 * Provides the scope and the open memory's id to [TravellingPicture].
 */
@Composable
fun PictureTravelLayer(openedId: String?, content: @Composable () -> Unit) {
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalOpenedMemoryId provides openedId,
        ) {
            content()
        }
    }
}

/**
 * Wraps a picture in a tile or row. Its picture is the one that flies: it is
 * visible only while its memory is not open, and the sheet's
 * [SheetPicture] takes its place. Visibility is the caller's here, so the
 * tile keeps its size while its picture is away. Outside a travel layer the
 * picture simply stays put.
 */
@Composable
fun TravellingPicture(memoryId: String, content: @Composable (Modifier) -> Unit) {
    val scope = LocalSharedTransitionScope.current
    if (scope == null) {
        content(Modifier)
        return
    }
    val open = LocalOpenedMemoryId.current == memoryId
    with(scope) {
        content(
            Modifier.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(key(memoryId)),
                visible = !open,
                boundsTransform = Travel,
            ),
        )
    }
}

/**
 * The detail's picture: the same key as the tile's, with the sheet's own
 * visibility scope, so the picture flies in with the sheet and back out.
 */
@Composable
fun AnimatedVisibilityScope.SheetPicture(memoryId: String, content: @Composable (Modifier) -> Unit) {
    val scope = LocalSharedTransitionScope.current
    if (scope == null) {
        content(Modifier)
        return
    }
    with(scope) {
        content(
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key(memoryId)),
                animatedVisibilityScope = this@SheetPicture,
                boundsTransform = Travel,
            ),
        )
    }
}
