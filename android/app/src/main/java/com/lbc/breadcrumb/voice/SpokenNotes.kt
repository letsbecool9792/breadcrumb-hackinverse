package com.lbc.breadcrumb.voice

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.data.SpokenNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Keeping and removing a note said over a memory ([SpokenNote]). The listen
 * queue hears a kept one, and what was said joins the memory's note.
 */
object SpokenNotes {

    /**
     * Keeps [recording] as the note said over each of [memoryIds] (several
     * when a share saved several): a copy for each, the recording itself for
     * the last. A note said before is replaced, and heard again. False when
     * none was kept; a memory deleted meanwhile keeps nothing.
     */
    suspend fun keep(app: BreadcrumbApp, memoryIds: List<String>, recording: Recording): Boolean =
        withContext(app.applicationScope.coroutineContext) {
            val ending = OriginalStore.SPOKEN.removePrefix(".")
            var kept = 0
            memoryIds.forEachIndexed { i, id ->
                try {
                    val file = if (i == memoryIds.lastIndex) {
                        app.originals.moveIn(recording.file, id, ending)
                    } else {
                        app.originals.copyIn(Uri.fromFile(recording.file), id, ending)
                    }
                    try {
                        app.database.spokenNoteDao().upsert(SpokenNote(memoryId = id, recordedAt = recording.startedAt))
                        kept++
                    } catch (_: SQLiteConstraintException) {
                        // The memory went while it was being said.
                        file.delete()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // This one isn't kept; the rest still are.
                }
            }
            recording.file.delete()
            kept > 0
        }

    /** Takes the note said over [memoryId] away, recording and all; what was said stays in its note. */
    suspend fun remove(app: BreadcrumbApp, memoryId: String) {
        withContext(app.applicationScope.coroutineContext) {
            app.database.spokenNoteDao().delete(memoryId)
            app.originals.spokenFile(memoryId)?.delete()
        }
    }
}
