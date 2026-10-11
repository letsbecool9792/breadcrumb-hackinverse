package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.common.CrumbTrail
import com.lbc.breadcrumb.ui.theme.Ink
import com.lbc.breadcrumb.ui.theme.InkOutline
import java.util.Locale

/**
 * The mosaic's first item (design §5): the wordmark large in the serif, and
 * under it the count kept, in capitals. A long press on the wordmark opens the
 * debug list; with [onLongPress] null there is no gesture at all.
 */
@Composable
internal fun Masthead(kept: String, onLongPress: (() -> Unit)?, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.home_wordmark),
            style = serif(48.sp, lineHeight = 52.sp),
            modifier = if (onLongPress == null) {
                Modifier
            } else {
                // Not a click: no ripple, and no click action for accessibility to announce.
                Modifier.pointerInput(onLongPress) {
                    detectTapGestures(onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    })
                }
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CrumbTrail(Modifier.size(15.dp))
            Text(
                kept.uppercase(Locale.ROOT),
                style = monoStyle(11.sp, InkOutline, letterSpacing = 1.4.sp),
            )
        }
    }
}

/**
 * Takes the masthead's place as it scrolls away (design §5). [collapse] is read
 * while drawing, so scrolling never recomposes it. A tap, only once it has
 * mostly arrived, goes back to the top.
 */
@Composable
internal fun SlimStrip(kept: String, collapse: () -> Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val c = collapse()
                alpha = c
                translationY = (1f - c) * -6.dp.toPx()
            }
            .background(Ink)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { if (collapse() > 0.5f) onClick() })
            }
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.home_wordmark), style = serif(20.sp, lineHeight = 22.sp))
        Text(kept, style = monoStyle(11.sp, InkOutline))
    }
}
