package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The crumb's bounce: it overshoots, and settles. */
private val DropEasing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)

/**
 * Plays a tile's [entrance] (design §6): a rise for the tiles the app opened on
 * and any a scroll reaches, a drop with the crumb's bounce for a memory that
 * arrives while the mosaic is up. Everything is read while drawing.
 */
@Composable
internal fun Modifier.entering(entrance: Entrance): Modifier {
    val progress = remember { Animatable(if (entrance is Entrance.None) 1f else 0f) }
    LaunchedEffect(entrance) {
        when (entrance) {
            Entrance.None -> Unit
            is Entrance.Rise -> {
                delay(entrance.delayMillis.toLong())
                progress.animateTo(1f, tween(MosaicEntrances.RISE_MILLIS, easing = FastOutSlowInEasing))
            }
            Entrance.Drop -> progress.animateTo(1f, tween(MosaicEntrances.DROP_MILLIS, easing = DropEasing))
        }
    }
    return when (entrance) {
        Entrance.None -> this
        is Entrance.Rise -> graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 22.dp.toPx()
        }
        Entrance.Drop -> graphicsLayer {
            val p = progress.value
            alpha = minOf(1f, 2.4f * p)
            translationY = (1f - p) * -40.dp.toPx()
            val scale = 0.84f + 0.16f * p
            scaleX = scale
            scaleY = scale
        }
    }
}
