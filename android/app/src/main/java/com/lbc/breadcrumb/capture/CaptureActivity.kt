package com.lbc.breadcrumb.capture

import android.content.ContentResolver
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.ocr.workScheduler
import com.lbc.breadcrumb.ui.capture.CaptureSheet
import com.lbc.breadcrumb.ui.capture.CaptureState
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import com.lbc.breadcrumb.voice.Recording
import com.lbc.breadcrumb.voice.SpokenNotes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * What the three capture activities share: the sheet and its state, the
 * note, Undo and closing. Each activity only captures: in
 * [capture] it calls [saveText] or [saveFiles], or [fail] with a reason.
 *
 * Saving runs in applicationScope, one file at a time, each row written right
 * after its copy: a capture activity is finished as soon as the person leaves
 * (noHistory), and whatever was copied by then is kept.
 */
abstract class CaptureActivity : ComponentActivity() {

    protected val app: BreadcrumbApp get() = application as BreadcrumbApp

    private val state = MutableStateFlow<CaptureState>(CaptureState.Saving)
    private val note = MutableStateFlow("")

    // Written from applicationScope, read on the main thread: guarded by this.
    private val saved = mutableListOf<Memory>()
    private var ended = false

    // What was shared and is kept already (roadmap 1): the memories kept, and how to keep each share
    // again as its own. A note typed then goes on the kept ones, once. Guarded by this.
    private val keptAlready = mutableListOf<Memory>()
    private val keepAgainWrites = mutableListOf<suspend () -> Memory?>()
    private var noteAdded = false

    private var saving: Job? = null

    /** Captures what this activity was opened for. Called once, from onCreate. */
    protected abstract fun capture()

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Re-created after the process died: the first instance saved already,
        // and capturing again would save everything twice.
        if (savedInstanceState != null) {
            finish()
            return
        }
        // Edge to edge on every version, as Android 15+ enforces at targetSdk 36,
        // so the sheet's own insets padding keeps it above the bar and keyboard.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val current by state.collectAsState()
            val typed by note.collectAsState()
            BreadcrumbTheme(darkTheme = true) {
                CaptureSheet(
                    state = current,
                    note = typed,
                    onNoteChange = { note.value = it },
                    onUndo = ::undo,
                    onKeepAgain = ::keepAgain,
                    onSay = ::say,
                    onDismiss = ::dismiss,
                )
            }
        }
        capture()
    }

    override fun onStop() {
        super.onStop()
        // noHistory finishes this activity as soon as the person leaves, so the
        // sheet's ending happens here too: a note left for dismiss would be
        // lost on Home.
        end()
    }

    /** Shows why nothing was saved, with a Close button. Never a silent close. */
    protected fun fail(reason: String) {
        state.value = CaptureState.Failed(reason)
    }

    /** Saves one memory made from shared or copied text, unless the same is kept already. */
    protected fun saveText(memory: Memory) {
        startSaving(total = 1) {
            val kept = SameMemory.keptLike(memory, app.database.captureDao().ofType(memory.type))
            if (kept != null) {
                keptAlready(kept) { memory.also { app.database.memoryDao().upsert(it) } }
            } else {
                app.database.memoryDao().upsert(memory)
                keep(memory)
            }
        }
    }

    /**
     * Saves each picture or PDF in [uris] in turn: copied into the store, then
     * its row written, before the next. content:// only: an exported activity
     * handed a file:// URI would copy the app's own private files. Anything
     * that is not a picture or a PDF is skipped. [text] goes on the file only
     * when exactly one is saved; [subject] titles each file it suits. A file
     * whose bytes are kept already writes no row, and its copy goes at once.
     */
    protected fun saveFiles(
        uris: List<Uri>,
        declaredType: String?,
        text: String?,
        subject: String?,
        source: Pair<String?, String?>,
    ) {
        startSaving(total = uris.size) {
            val files = uris
                .filter { it.scheme == ContentResolver.SCHEME_CONTENT }
                .mapNotNull { uri ->
                    val mime = MediaRules.mimeOf(CaptureStorage.providerType(app, uri), declaredType)
                    MediaRules.kindOf(mime)?.let { kind -> Triple(uri, mime, kind) }
                }
            if (files.isEmpty()) {
                fail(app.getString(R.string.capture_nothing_to_save))
                return@startSaving
            }

            val caption = MediaRules.captionFor(text, files.size)
            val hasLink = caption?.let { TextRules.parseText(it).second } ?: false
            for ((uri, _, _) in files) {
                val copy: suspend () -> Memory? = {
                    CaptureStorage.copyFile(
                        app = app,
                        uri = uri,
                        declaredType = declaredType,
                        rawText = caption,
                        hasLink = hasLink,
                        subject = subject,
                        sourceApp = source.first,
                        sourceAppLabel = source.second,
                    )
                }
                val copied = copy() ?: continue
                val kept = CaptureStorage.keptFile(app, copied)
                if (kept != null) {
                    // Keep again copies it once more, while the share's grant still holds.
                    app.originals.delete(copied)
                    keptAlready(kept) { copy()?.let { CaptureStorage.write(app, it) } }
                } else {
                    CaptureStorage.write(app, copied)?.let(::keep)
                }
            }
        }
    }

    private fun startSaving(total: Int, work: suspend () -> Unit) {
        saving = app.applicationScope.launch {
            work()
            val (kept, already, repeats) = synchronized(this@CaptureActivity) {
                Triple(saved.toList(), keptAlready.toList(), keepAgainWrites.size)
            }
            state.value = when {
                kept.isNotEmpty() -> CaptureState.Saved(kept, total, keptAlready = repeats)
                already.isNotEmpty() -> CaptureState.KeptAlready(already)
                state.value is CaptureState.Failed -> state.value
                total == 0 -> CaptureState.Failed(app.getString(R.string.capture_nothing_to_save))
                else -> CaptureState.Failed(app.getString(R.string.capture_failed))
            }
        }
    }

    /** A memory is saved: remembered, for the note and for an undo. */
    private fun keep(memory: Memory) {
        synchronized(this) { saved += memory }
    }

    /** What was shared is kept already as [kept]: nothing is written, unless [again] is asked for. */
    private fun keptAlready(kept: Memory, again: suspend () -> Memory?) {
        synchronized(this) {
            if (keptAlready.none { it.id == kept.id }) keptAlready += kept
            keepAgainWrites += again
        }
    }

    /** "Keep again": what was kept already is saved once more, each a memory of its own. */
    private fun keepAgain() {
        val writes = synchronized(this) {
            keptAlready.clear()
            keepAgainWrites.toList().also { keepAgainWrites.clear() }
        }
        state.value = CaptureState.Saving
        val before = saving
        saving = app.applicationScope.launch {
            before?.join()
            for (write in writes) write()?.let(::keep)
            val kept = synchronized(this@CaptureActivity) { saved.toList() }
            state.value = if (kept.isNotEmpty()) {
                CaptureState.Saved(kept)
            } else {
                CaptureState.Failed(app.getString(R.string.capture_failed))
            }
        }
    }

    /**
     * A note said over what was saved, or over what is kept already: a copy of
     * the recording for each. The listen queue hears it into their note.
     */
    private suspend fun say(recording: Recording): Boolean {
        saving?.join()
        val ids = synchronized(this) { saved.ifEmpty { keptAlready }.map { it.id } }
        return SpokenNotes.keep(app, ids, recording)
    }

    /**
     * Deletes what this capture saved, once its writes are done (a row still
     * being written would outlive a delete that ran first), and shows
     * "Removed" while it does.
     */
    private fun undo() {
        state.value = CaptureState.Removed
        val writes = saving
        app.applicationScope.launch {
            writes?.join()
            val removed = synchronized(this@CaptureActivity) { saved.toList().also { saved.clear() } }
            for (memory in removed) {
                app.database.memoryDao().delete(memory)
                app.originals.delete(memory)
            }
        }
    }

    private fun dismiss() {
        end()
        finishCleanly()
    }

    /**
     * The sheet is going, dismissed or left: write whatever note is typed, then
     * schedule what follows a save (a link's page, the embedding), so the note
     * is in what is embedded. The call happens once; the note whenever it is
     * typed. With nothing new saved, the note is added under the note of what
     * is kept already, once.
     */
    private fun end() {
        val typed = note.value.trim()
        val first = synchronized(this) { (!ended).also { ended = true } }
        val writes = saving
        app.applicationScope.launch {
            writes?.join()
            val (ids, keptIds) = synchronized(this@CaptureActivity) {
                val keptIds = keptAlready.map { it.id }.takeIf { saved.isEmpty() && typed.isNotEmpty() && !noteAdded }.orEmpty()
                if (keptIds.isNotEmpty()) noteAdded = true
                saved.map { it.id } to keptIds
            }
            if (typed.isNotEmpty() && ids.isNotEmpty()) app.database.captureDao().setNote(ids, typed)
            if (keptIds.isNotEmpty()) app.database.captureDao().addNote(keptIds, typed)
            if (first || keptIds.isNotEmpty()) workScheduler(app)
        }
    }

    /**
     * Closes without anything sliding away after the sheet. Closing a capture
     * activity is a cross-task transition the app cannot animate, and whatever
     * the window still draws goes with it: so the window stops taking touches
     * and hides, and the activity finishes two frames later, once the window
     * manager has hidden the surface. A hidden window may stop receiving
     * frames, so a timer finishes it regardless.
     */
    private fun finishCleanly() {
        if (isFinishing) return
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        val decor = window.decorView
        decor.visibility = View.INVISIBLE
        decor.postOnAnimation { decor.postOnAnimation { finish() } }
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, FINISH_FALLBACK_MS)
    }

    private companion object {
        const val FINISH_FALLBACK_MS = 150L
    }
}
