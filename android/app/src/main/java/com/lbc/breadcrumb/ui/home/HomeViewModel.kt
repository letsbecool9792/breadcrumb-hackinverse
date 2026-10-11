package com.lbc.breadcrumb.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.sync.UploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * What the home screen holds (design §4). The memories come straight from Room,
 * newest first, so a save made in another process shows up by itself; null
 * until Room has answered, so the screen draws nothing rather than flash
 * "Nothing kept yet".
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BreadcrumbApp

    val memories: StateFlow<List<Memory>?> = app.database.memoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Held here, not by the composition, so a rotation replays no entrance. */
    internal val entrances = MosaicEntrances()

    /** Which result rows have arrived in the search on screen; held here for the same reason. */
    internal val arrivals = SearchArrivals()

    private val typed = MutableStateFlow("")

    /** What is typed in the search field. */
    fun setQuery(query: String) {
        typed.value = query
    }

    /**
     * The search (design §9): the phone's own words at once, the server's ranking
     * after a pause, and the words alone when the server is out of reach.
     */
    val search: StateFlow<SearchState> = twoStageSearch(
        queries = typed,
        wordSearch = { app.database.readingDao().search(it, MATCHES) },
        serverSearch = { app.server.search(it, MATCHES) },
        heldMemories = { app.database.memoryDao().getAll() },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Resting)

    init {
        // Whatever a killed process left, or a server that came back, goes now.
        UploadWorker.schedule(app)
    }

    private companion object {
        const val MATCHES = 30
    }
}
