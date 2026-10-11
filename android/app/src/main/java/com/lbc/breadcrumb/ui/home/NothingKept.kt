package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.CrumbFaint
import com.lbc.breadcrumb.ui.theme.InkOutline

/**
 * An empty mosaic says what to do (design §7), with a trail of crumbs behind it
 * walking towards where the "+" will be. The lap is read while drawing.
 */
@Composable
internal fun NothingKept(modifier: Modifier = Modifier) {
    val lap = rememberInfiniteTransition(label = "trail").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "lap",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val phase = lap.value
            for (i in 0 until MosaicMath.TRAIL_CRUMBS) {
                val t = i / (MosaicMath.TRAIL_CRUMBS - 1).toFloat()
                val lit = MosaicMath.trailLight(phase, i)
                drawCircle(
                    color = lerp(CrumbFaint, AmberBright, 0.25f + 0.75f * t).copy(alpha = 0.45f + 0.55f * lit),
                    radius = (2.2f + 2.4f * t).dp.toPx() * (0.85f + 0.3f * lit),
                    center = MosaicMath.trailPoint(i, size.width, size.height, 26.dp.toPx(), 30.dp.toPx()),
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 26.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.home_nothing_title), style = serif(22.sp))
            Text(stringResource(R.string.home_nothing_start), style = sans(15.sp, lineHeight = 22.sp))
            Text(stringResource(R.string.home_nothing_or), style = monoStyle(11.sp, InkOutline, lineHeight = 18.sp))
        }
    }
}
