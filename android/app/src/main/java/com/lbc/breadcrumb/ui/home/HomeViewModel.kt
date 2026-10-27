package com.lbc.breadcrumb.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.SpokenNote
import com.lbc.breadcrumb.embed.EmbedWorker
import com.lbc.breadcrumb.embed.LocalSearch
import com.lbc.breadcrumb.embed.PhraseRules
import com.lbc.breadcrumb.embed.VectorIndex
import com.lbc.breadcrumb.embed.Sites
import com.lbc.breadcrumb.imports.BulkImporter
import com.lbc.breadcrumb.voice.Recording
import com.lbc.breadcrumb.voice.SpokenNotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

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

    /** The notes said over memories being deleted, held until Undo brings them back with their memory. */
    private val heldSpoken = ConcurrentHashMap<String, SpokenNote>()

    /**
     * Delete with five seconds of Undo (design §12); its changes outlive the screen.
     * A note said over the memory goes with its row, and comes back with it; its
     * recording goes with the original.
     */
    private val deletes = UndoableDeletes(
        scope = app.applicationScope,
        deleteRow = { memory ->
            app.database.spokenNoteDao().get(memory.id)?.let { heldSpoken[memory.id] = it }
            app.database.memoryDao().delete(memory)
        },
        restoreRow = { memory ->
            app.database.memoryDao().upsert(memory)
            heldSpoken.remove(memory.id)?.let { app.database.spokenNoteDao().upsert(it) }
        },
        deleteOriginal = { memory ->
            heldSpoken.remove(memory.id)
            app.originals.delete(memory)
        },
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

    /** The note said over a memory, live, or null. */
    fun spokenNote(id: String): Flow<SpokenNote?> = app.database.spokenNoteDao().observe(id)

    /** Keeps a note said over a memory; the listen queue hears it into the memory's note. */
    suspend fun keepSpoken(id: String, recording: Recording): Boolean = SpokenNotes.keep(app, listOf(id), recording)

    fun removeSpoken(id: String) {
        app.applicationScope.launch { SpokenNotes.remove(app, id) }
    }

    override fun onCleared() {
        // Gone mid-undo: make the delete final rather than leave a file behind.
        deletes.finish()
    }

    private companion object {
        const val MATCHES = 30
    }
}
