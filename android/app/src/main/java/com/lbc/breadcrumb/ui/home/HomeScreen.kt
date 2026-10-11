package com.lbc.breadcrumb.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.ui.theme.Ink

/**
 * The app's face (design §4): the mosaic of everything kept, or the ranked list
 * while searching, with the one search field underneath. The "+" and the detail
 * sheet join it in U5 and U6.
 *
 * [onOpenDebug] is passed only in debug builds; null, the wordmark has no
 * gesture at all.
 */
@Composable
fun HomeScreen(
    onOpenDebug: (() -> Unit)?,
    onOpen: (Memory) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val gridState = rememberLazyStaggeredGridState()

    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }
    // Back clears a search before it leaves the app.
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    val searching = searchState is SearchState.Searching
    // The search being left stays drawn while it fades out.
    val lastSearch = remember { LastSearch() }
    (searchState as? SearchState.Searching)?.let { lastSearch.value = it }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val hideKeyboardOnDrag = remember(keyboard, focus) { HideKeyboardOnDrag(keyboard, focus) }

    // One layer over everything, so a picture can travel between its tile and the sheet.
    PictureTravelLayer(openedId = null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .statusBarsPadding(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(hideKeyboardOnDrag),
            ) {
                // While Room has not answered, nothing is drawn.
                memories?.let { list ->
                    // One "now" per screenful, so ages don't tick apart row by row.
                    val now = remember(list, searching) { System.currentTimeMillis() }
                    remember(list) { viewModel.entrances.submit(list.map { it.id }) }
                    val kept = ResultText.kept(list.size, list.count { it.syncState != SyncState.SYNCED })

                    AnimatedContent(
                        targetState = searching,
                        transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                        modifier = Modifier.fillMaxSize(),
                        label = "browse or search",
                    ) { isSearching ->
                        val shown = lastSearch.value
                        if (isSearching && shown != null) {
                            SearchResults(
                                state = shown,
                                total = list.size,
                                now = now,
                                arrivals = viewModel.arrivals,
                                onOpen = onOpen,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Mosaic(
                                memories = list,
                                kept = kept,
                                now = now,
                                entrances = viewModel.entrances,
                                state = gridState,
                                onOpen = onOpen,
                                onLongPressWordmark = onOpenDebug,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                // Content fades under the search field rather than stopping at it.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Ink))),
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            ) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    searching = searching,
                    ranking = (searchState as? SearchState.Searching)?.status == SearchStatus.RANKING,
                )
            }
        }
    }
}

private class LastSearch {
    var value: SearchState.Searching? = null
}

/** A drag on the content puts the keyboard away; a list moving on its own leaves it be. */
private class HideKeyboardOnDrag(
    private val keyboard: SoftwareKeyboardController?,
    private val focus: FocusManager,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
            keyboard?.hide()
            focus.clearFocus()
        }
        return Offset.Zero
    }
}
