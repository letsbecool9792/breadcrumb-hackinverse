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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * What the three capture activities share: the sheet and its state, the
 * upload hold, the note, Undo and closing. Each activity only captures: in
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

    /** Saves one memory made from shared or copied text. */
    protected fun saveText(memory: Memory) {
        startSaving(total = 1) {
            app.database.memoryDao().upsert(memory)
            keep(memory)
        }
    }

    /**
     * Saves each picture or PDF in [uris] in turn: copied into the store, then
     * its row written, before the next. content:// only: an exported activity
     * handed a file:// URI would copy the app's own private files. Anything
     * that is not a picture or a PDF is skipped. [text] goes on the file only
     * when exactly one is saved; [subject] titles each file it suits.
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
                val memory = CaptureStorage.saveFile(
                    app = app,
                    uri = uri,
                    declaredType = declaredType,
                    rawText = caption,
                    hasLink = hasLink,
                    subject = subject,
                    sourceApp = source.first,
                    sourceAppLabel = source.second,
                )
                if (memory != null) {
                    keep(memory)
                }
            }
        }
    }

    private fun startSaving(total: Int, work: suspend () -> Unit) {
        saving = app.applicationScope.launch {
            work()
            val kept = synchronized(this@CaptureActivity) { saved.toList() }
            state.value = when {
                kept.isNotEmpty() -> CaptureState.Saved(kept, total)
                state.value is CaptureState.Failed -> state.value
                total == 0 -> CaptureState.Failed(app.getString(R.string.capture_nothing_to_save))
                else -> CaptureState.Failed(app.getString(R.string.capture_failed))
            }
        }
    }

    /** A memory is saved: held back from upload while the sheet is open. */
    private fun keep(memory: Memory) {
        synchronized(this) {
            saved += memory
            if (!ended) app.uploadHolds += memory.id
        }
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
            val now = System.currentTimeMillis()
            for (memory in removed) {
                app.database.memoryDao().deleteEverywhere(memory, now)
                app.originals.delete(memory)
            }
            app.uploadHolds.removeAll(removed.map { it.id }.toSet())
        }
    }

    private fun dismiss() {
        end()
        finishCleanly()
    }

    /**
     * The sheet is going, dismissed or left: write whatever note is typed, then
     * release the hold, then tell the upload queue. In that order: released
     * first, the queue could send the memories without the note and then again
     * with it. The hold and the call happen once; the note whenever it is typed.
     */
    private fun end() {
        val typed = note.value.trim()
        val first = synchronized(this) { (!ended).also { ended = true } }
        val writes = saving
        app.applicationScope.launch {
            writes?.join()
            val ids = synchronized(this@CaptureActivity) { saved.map { it.id } }
            if (typed.isNotEmpty() && ids.isNotEmpty()) app.database.captureDao().setNote(ids, typed)
            if (first) {
                app.uploadHolds.removeAll(ids.toSet())
                workScheduler(app)
            }
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
