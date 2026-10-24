package com.lbc.breadcrumb.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.embed.EmbedWorker
import com.lbc.breadcrumb.embed.LocalSearch
import com.lbc.breadcrumb.embed.PhraseRules
import com.lbc.breadcrumb.embed.VectorIndex
import com.lbc.breadcrumb.embed.Sites
import com.lbc.breadcrumb.imports.BulkImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    /** Bringing in what is already on the phone; its line shows over the search field. */
    internal val importer: BulkImporter get() = app.importer

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
     * The search (design §9): the phone's own words at once, then the ranking
     * after a pause, both narrowed by the phrase's filters. All of it runs on
     * the phone (local-first L2 and L6): nothing typed leaves it.
     */
    private val localSearch = LocalSearch(
        embed = { app.embedder.text(it) },
        vectors = VectorIndex(app.database.embeddingDao())::current,
        words = { query, limit -> app.database.readingDao().search(query, limit) },
        parse = { phrase -> PhraseRules.parse(phrase, LocalDate.now(), Sites.sourcesOf(app.database.memoryDao().getAll())) },
        memories = { app.database.memoryDao().getAll() },
    )

    val search: StateFlow<SearchState> = twoStageSearch(
        queries = typed,
        wordSearch = { localSearch.byWords(it, MATCHES) },
        rankedSearch = { localSearch.search(it, MATCHES) },
        heldMemories = { app.database.memoryDao().getAll() },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Resting)

    /** Delete with five seconds of Undo (design §12); its changes outlive the screen. */
    private val deletes = UndoableDeletes(
        scope = app.applicationScope,
        deleteRow = { app.database.memoryDao().delete(it) },
        restoreRow = { app.database.memoryDao().upsert(it) },
        deleteOriginal = { app.originals.delete(it) },
        // A memory brought back lost its vector with the row: embed it again.
        scheduleWork = { EmbedWorker.schedule(app) },
    )

    /** The memory just deleted, that can still be brought back; null once the five seconds pass. */
    val pendingDelete: StateFlow<Memory?> = deletes.pending

    fun delete(memory: Memory) = deletes.delete(memory)

    fun undoDelete() = deletes.undo()

    /** Writes the person's note on a memory (null clears it), and embeds it again: the note is part of what is embedded. */
    fun setNote(id: String, note: String?) {
        app.applicationScope.launch {
            if (app.database.captureDao().setNote(listOf(id), note) > 0) EmbedWorker.schedule(app)
        }
    }

    override fun onCleared() {
        // Gone mid-undo: make the delete final rather than leave a file behind.
        deletes.finish()
    }

    private companion object {
        const val MATCHES = 30
    }
}
