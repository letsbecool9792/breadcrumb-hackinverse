package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ui.common.CrumbTrail
import com.lbc.breadcrumb.ui.common.DocumentGlyph
import com.lbc.breadcrumb.ui.common.NoteGlyph
import com.lbc.breadcrumb.ui.common.rememberThumbnail
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.HitBackground
import com.lbc.breadcrumb.ui.theme.InkBorder
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkMedia
import com.lbc.breadcrumb.ui.theme.InkOutline
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

/**
 * A search on screen (design §9): the counter line over the results, or what to
 * say when there are none. Stateless; the screen holds the search.
 */
@Composable
internal fun SearchResults(
    state: SearchState.Searching,
    total: Int,
    now: Long,
    arrivals: SearchArrivals,
    onOpen: (Memory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // One tick when the ranked answer lands with something in it.
    val rankedWithResults = state.status == SearchStatus.RANKED && state.results.isNotEmpty()
    LaunchedEffect(state.phrase, rankedWithResults) {
        if (rankedWithResults) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    remember(state.phrase) { arrivals.begin(state.phrase) }

    Column(modifier) {
        Text(
            ResultText.counter(
                shown = state.results.size,
                total = total,
                typed = state.phrase,
                interpretation = state.interpretation,
                today = LocalDate.now(),
                status = state.status,
            ),
            style = monoStyle(11.sp, InkOutline, letterSpacing = 0.3.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
        )
        if (state.results.isEmpty()) {
            NoResults(state.status)
        } else {
            val words = remember(state.phrase, state.interpretation) {
                wordsToLight(state.phrase, state.interpretation?.query)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.results, key = { _, result -> result.memory.id }) { index, result ->
                    ResultRow(
                        result = result,
                        index = index,
                        words = words,
                        now = now,
                        raised = index == 0 && state.status == SearchStatus.RANKED,
                        arrivals = arrivals,
                        onClick = { onOpen(result.memory) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(140),
                        ),
                    )
                }
            }
        }
    }
}

/** Nothing to show: crumbs walking while the answer is on its way, else why not. */
@Composable
private fun NoResults(status: SearchStatus) {
    when (status) {
        SearchStatus.RANKING -> CrumbTrail(Modifier.padding(start = 28.dp, top = 36.dp).size(44.dp), walking = true)
        SearchStatus.RANKED -> Quiet(stringResource(R.string.search_none_title), stringResource(R.string.search_none_hint))
        SearchStatus.OFFLINE, SearchStatus.UNAVAILABLE ->
            Quiet(stringResource(R.string.search_words_title), stringResource(R.string.search_words_hint))
    }
}

/**
 * One result (design §9): its picture or mark, its title, why it matched with
 * the hit lit, and its age and source. A row arrives once per search, and its
 * matched word warms a moment after it lands.
 */
@Composable
private fun ResultRow(
    result: SearchResult,
    index: Int,
    words: List<String>,
    now: Long,
    raised: Boolean,
    arrivals: SearchArrivals,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val memory = result.memory
    val text = rowText(memory, words)

    // Decided once, the first time this row is drawn in this search.
    val arrivalDelay = remember(memory.id) { arrivals.delayFor(memory.id, index) }
    val arrival = remember(memory.id) { Animatable(if (arrivalDelay == null) 1f else 0f) }
    val lit = remember(memory.id) { Animatable(if (arrivalDelay == null) 1f else 0f) }
    LaunchedEffect(memory.id) {
        if (arrivalDelay != null) {
            delay(arrivalDelay)
            arrival.animateTo(1f, tween(SearchArrivals.ARRIVE_MILLIS, easing = FastOutSlowInEasing))
            delay(SearchArrivals.LIGHT_AFTER_MILLIS)
            lit.animateTo(1f, tween(SearchArrivals.WARM_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = arrival.value
                alpha = p
                translationY = (1f - p) * 16.dp.toPx()
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (raised) InkElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ResultThumbnail(memory)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text.title,
                style = sans(16.sp, weight = FontWeight.Medium, lineHeight = 21.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (val why = text.why) {
                is Why.Fragment -> Text(
                    buildAnnotatedString {
                        append(why.lead)
                        withStyle(
                            SpanStyle(
                                color = lerp(BoneDim, AmberBright, lit.value),
                                background = HitBackground.copy(alpha = lit.value),
                            ),
                        ) { append(why.hit) }
                        append(why.tail)
                    },
                    style = monoStyle(11.sp, BoneDim, lineHeight = 16.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                is Why.Summary -> Text(
                    why.text,
                    style = monoStyle(11.sp, BoneDim, lineHeight = 16.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                null -> Unit
            }
            Text(ResultText.rowMeta(memory, now), style = metaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The 46 dp mark at the left of a row: the picture, the page, the host's initial, or a note's lines. */
@Composable
private fun ResultThumbnail(memory: Memory) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(46.dp)
            .clip(shape)
            .background(InkMedia)
            .border(1.dp, InkBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        when (memory.type) {
            MemoryType.IMAGE, MemoryType.PDF -> {
                val thumbnail = rememberThumbnail(memory, 140)
                TravellingPicture(memory.id) { travel ->
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            modifier = travel.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                        )
                    } else if (memory.type == MemoryType.PDF) {
                        DocumentGlyph(Modifier.size(width = 24.dp, height = 30.dp))
                    }
                }
            }
            MemoryType.LINK -> Text(
                memory.rawText?.let(ResultText::host)?.first()?.uppercase(Locale.ROOT) ?: "↗",
                style = monoStyle(16.sp, AmberBright),
            )
            MemoryType.TEXT, MemoryType.AUDIO -> NoteGlyph(Modifier.fillMaxSize().padding(9.dp))
        }
    }
}
