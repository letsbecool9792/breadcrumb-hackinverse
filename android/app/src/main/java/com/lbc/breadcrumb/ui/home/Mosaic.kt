package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.ui.theme.AmberContainerDark
import com.lbc.breadcrumb.ui.theme.Ink
import com.lbc.breadcrumb.ui.theme.InkOutline
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Every memory, newest first, as a grid of tiles (design §6), under the
 * masthead that hands over to a slim strip as it scrolls away, with the month
 * of what is in view at the right edge while it moves. Empty, it says what to
 * do (§7).
 *
 * Stateless: the memories, the entrances and the scroll state come from the
 * screen, so a search and back returns to the same place and replays nothing.
 */
@Composable
internal fun Mosaic(
    memories: List<Memory>,
    kept: String,
    now: Long,
    entrances: MosaicEntrances,
    state: LazyStaggeredGridState,
    onOpen: (Memory) -> Unit,
    onLongPressWordmark: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 56.dp,
    onKept: (() -> Unit)? = null,
) {
    if (memories.isEmpty()) {
        Column(modifier.padding(horizontal = 14.dp)) {
            Masthead(kept, onLongPressWordmark)
            NothingKept(Modifier.weight(1f).fillMaxSize())
        }
        return
    }

    var mastheadHeight by remember { mutableIntStateOf(0) }
    val collapse = remember {
        derivedStateOf {
            mastheadCollapse(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, mastheadHeight)
        }
    }
    val showStrip by remember { derivedStateOf { collapse.value > 0f } }
    val scope = rememberCoroutineScope()

    Box(modifier) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            item(key = MASTHEAD_KEY, span = StaggeredGridItemSpan.FullLine) {
                Masthead(kept, onLongPressWordmark, Modifier.onSizeChanged { mastheadHeight = it.height }, onKept)
            }
            itemsIndexed(memories, key = { _, memory -> memory.id }) { index, memory ->
                // Decided once, the first time this tile is drawn.
                val entrance = remember(memory.id) { entrances.entranceFor(memory.id, index) }
                MemoryTile(
                    memory = memory,
                    now = now,
                    onClick = { onOpen(memory) },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(200),
                        )
                        .entering(entrance),
                )
            }
        }

        if (showStrip) {
            SlimStrip(
                kept = kept,
                collapse = { collapse.value },
                onClick = { scope.launch { state.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        val month by remember(memories) {
            derivedStateOf {
                val tile = memories.getOrNull((state.firstVisibleItemIndex - 1).coerceAtLeast(0))
                tile?.let { monthOfMillis(it.capturedAt) }
            }
        }
        month?.let { DateScrubber(it, state.isScrollInProgress, Modifier.align(Alignment.CenterEnd)) }
    }
}

private const val MASTHEAD_KEY = "masthead"

private fun monthOfMillis(millis: Long): String =
    ResultText.monthOf(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()).uppercase(Locale.ROOT)

/**
 * The month of the first tile in view, in place of section headers (design §6).
 * Fully there while the grid scrolls, fading once it stops. Read while drawing.
 */
@Composable
private fun DateScrubber(month: String, scrolling: Boolean, modifier: Modifier = Modifier) {
    val visibility = animateFloatAsState(
        targetValue = if (scrolling) 1f else 0f,
        animationSpec = tween(if (scrolling) 120 else 700),
        label = "scrubber",
    )
    Row(
        modifier
            .graphicsLayer { alpha = visibility.value }
            .background(Ink)
            .padding(end = 12.dp, top = 3.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(16.dp).height(1.dp).background(AmberContainerDark))
        Text(month, style = monoStyle(10.sp, InkOutline, letterSpacing = 1.sp))
    }
}
