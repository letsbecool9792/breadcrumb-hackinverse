package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.SyncState
import com.lbc.breadcrumb.ui.theme.Ink

/**
 * The app's face (design §4): the mosaic of everything kept. The search field,
 * the "+" and the detail sheet join it here.
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
    val gridState = rememberLazyStaggeredGridState()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // One layer over everything, so a picture can travel between its tile and the sheet.
    PictureTravelLayer(openedId = null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .statusBarsPadding(),
        ) {
            // While Room has not answered, nothing is drawn.
            memories?.let { list ->
                // One "now" per screenful, so ages don't tick apart row by row.
                val now = remember(list) { System.currentTimeMillis() }
                remember(list) { viewModel.entrances.submit(list.map { it.id }) }
                val kept = ResultText.kept(list.size, list.count { it.syncState != SyncState.SYNCED })
                Mosaic(
                    memories = list,
                    kept = kept,
                    now = now,
                    entrances = viewModel.entrances,
                    state = gridState,
                    onOpen = onOpen,
                    onLongPressWordmark = onOpenDebug,
                    modifier = Modifier.fillMaxSize(),
                    bottomPadding = 56.dp + navigationBarBottom,
                )
            }
        }
    }
}
