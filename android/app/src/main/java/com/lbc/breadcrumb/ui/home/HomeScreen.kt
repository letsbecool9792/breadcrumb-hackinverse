package com.lbc.breadcrumb.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.embed.ModelStatus
import com.lbc.breadcrumb.imports.ImportBar
import com.lbc.breadcrumb.open.Originals
import com.lbc.breadcrumb.ui.keep.rememberKeepState
import com.lbc.breadcrumb.ui.theme.Ink

/**
 * The app's face (design §4): the mosaic of everything kept, or the ranked list
 * while searching, with the one search field underneath and the "+" beside it,
 * and one memory opened in a sheet over either, or the "+" sheet.
 *
 * [onOpenDebug] is passed only in debug builds; null, the wordmark has no
 * gesture at all. [keepRequests] counts the launcher shortcut's requests; each
 * new one opens the "+" sheet. [voiceRequests] counts "Say something"'s; each
 * new one opens it on the voice panel. While the model isn't ready, [modelStatus]
 * shows over the search field, and a tap on it is [onModelBar].
 */
@Composable
fun HomeScreen(
    onOpenDebug: (() -> Unit)?,
    keepRequests: Int = 0,
    voiceRequests: Int = 0,
    modelStatus: ModelStatus = ModelStatus.Ready,
    onModelBar: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val gridState = rememberLazyStaggeredGridState()
    val context = LocalContext.current

    // The memory opened in the sheet, by id: the row is live, so what is copied back appears in place.
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    // The "+" sheet's panel while it is open. The draft outlives the sheet: it is stored as it changes.
    var plus by rememberSaveable { mutableStateOf<PlusPanel?>(null) }
    val keepState = rememberKeepState()

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
    // Opening a memory puts the keyboard away first, so the sheet never rises behind it.
    val open: (Memory) -> Unit = { memory ->
        keyboard?.hide()
        focus.clearFocus()
        openedId = memory.id
    }
    val openPlus: () -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
        plus = PlusPanel.opening(nothingKept = memories?.isEmpty() == true)
    }

    // Each new request from the launcher shortcut opens the composer, over any memory open.
    var keepRequestsAnswered by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(keepRequests) {
        if (keepRequests > keepRequestsAnswered) {
            keepRequestsAnswered = keepRequests
            keyboard?.hide()
            focus.clearFocus()
            openedId = null
            plus = PlusPanel.COMPOSER
        }
    }
    var voiceRequestsAnswered by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(voiceRequests) {
        if (voiceRequests > voiceRequestsAnswered) {
            voiceRequestsAnswered = voiceRequests
            keyboard?.hide()
            focus.clearFocus()
            openedId = null
            plus = PlusPanel.VOICE
        }
    }

    // One layer over everything, so a picture can travel between its tile and the sheet.
    PictureTravelLayer(openedId = openedId) {
        Box(Modifier.fillMaxSize()) {
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
                        val kept = ResultText.kept(list.size)

                        AnimatedContent(
                            targetState = searching,
                            transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                            modifier = Modifier.fillMaxSize(),
                            label = "browse or search",
                        ) { isSearching ->
                            val shown = lastSearch.value
                            if (isSearching && shown != null) {
                                // The live rows, so a note written or a memory deleted shows at once.
                                val live = list.associateBy { it.id }
                                val current = shown.copy(
                                    results = shown.results.mapNotNull { r -> live[r.memory.id]?.let { r.copy(memory = it) } },
                                )
                                SearchResults(
                                    state = current,
                                    total = list.size,
                                    now = now,
                                    arrivals = viewModel.arrivals,
                                    onOpen = open,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Mosaic(
                                    memories = list,
                                    kept = kept,
                                    now = now,
                                    entrances = viewModel.entrances,
                                    state = gridState,
                                    onOpen = open,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ModelBar(status = modelStatus, onOpen = onModelBar)
                    ImportBar(importer = viewModel.importer)
                    UndoBar(
                        what = pendingDelete?.let { undoLine(ResultText.title(it)) },
                        onUndo = viewModel::undoDelete,
                    )
                    SearchRow(
                        query = query,
                        onQueryChange = { query = it },
                        searching = searching,
                        ranking = (searchState as? SearchState.Searching)?.status == SearchStatus.RANKING,
                        hasDraft = keepState.hasDraft,
                        onPlus = openPlus,
                    )
                }
            }

            // The memory opened, in the app's own sheet so its picture can travel into it.
            val list = memories
            val opened = list?.firstOrNull { it.id == openedId }
            // A memory deleted or cleared while open closes its sheet.
            LaunchedEffect(opened == null, list != null) { if (opened == null && list != null) openedId = null }
            SheetLayer(item = opened, onDismiss = { openedId = null }) { memory, visibility ->
                val now = remember(memory.id) { System.currentTimeMillis() }
                val actions = remember(memory) {
                    val action = Originals.actionFor(context, memory)
                    val share = Originals.shareFor(context, memory)
                    DetailActions(
                        main = action,
                        onMain = { action.open(context) },
                        onShare = share?.let { s -> { s.share(context) } },
                        onDelete = {
                            viewModel.delete(memory)
                            openedId = null
                        },
                        onNote = { viewModel.setNote(memory.id, it) },
                    )
                }
                MemoryDetail(memory, now, visibility, actions)
            }

            // Keeping something from inside the app, or bringing in what's already on the phone.
            SheetLayer(item = plus, onDismiss = { plus = null }) { panel, _ ->
                PlusSheet(
                    panel = panel,
                    keepState = keepState,
                    importer = viewModel.importer,
                    onPanel = { plus = it },
                    onClose = { plus = null },
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
