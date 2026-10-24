package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Delete with five seconds of Undo instead of a confirmation (design §12).
 *
 * - [delete]: the row goes at once ([deleteRow]), so it leaves the mosaic and
 *   the results immediately. The original file stays.
 * - [undo]: the row comes back ([restoreRow]), and [scheduleWork] has it
 *   embedded again, since its vector went with the row.
 * - Five seconds pass, or a second delete comes, or [finish] is called (the
 *   view model is cleared mid-undo): the delete is final, and the file goes.
 *
 * Every database change runs one at a time on [scope], in the order asked, so
 * a restore can never overtake the delete it undoes. [scope] is the
 * application's, so a delete finishes even when the screen is gone.
 */
internal class UndoableDeletes(
    private val scope: CoroutineScope,
    private val deleteRow: suspend (Memory) -> Unit,
    private val restoreRow: suspend (Memory) -> Unit,
    private val deleteOriginal: (Memory) -> Unit,
    private val scheduleWork: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    /** Waits out the undo window. */
    private val undoWindow: suspend () -> Unit = { delay(UNDO_MILLIS) },
) {
    private val _pending = MutableStateFlow<Memory?>(null)

    /** The memory that can still be brought back, or null. */
    val pending: StateFlow<Memory?> = _pending

    private val lock = Any()
    private var window: Job? = null
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in work) {
                try {
                    job()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // One failed change must not stop the ones behind it.
                }
            }
        }
    }

    fun delete(memory: Memory) {
        synchronized(lock) {
            window?.cancel()
            // A second delete makes the first final.
            _pending.value?.let(::makeFinal)
            _pending.value = memory
            work.trySend { deleteRow(memory) }
            window = scope.launch {
                undoWindow()
                synchronized(lock) {
                    if (_pending.value?.id == memory.id) makeFinal(memory)
                }
            }
        }
    }

    fun undo() {
        val memory = synchronized(lock) {
            val memory = _pending.value ?: return
            window?.cancel()
            _pending.value = null
            memory
        }
        work.trySend {
            restoreRow(memory.copy(updatedAt = now()))
            scheduleWork()
        }
    }

    /** The screen is gone mid-undo: make the delete final now rather than leave a file behind. */
    fun finish() {
        synchronized(lock) {
            window?.cancel()
            _pending.value?.let(::makeFinal)
        }
    }

    private fun makeFinal(memory: Memory) {
        _pending.value = null
        work.trySend { deleteOriginal(memory) }
    }

    companion object {
        const val UNDO_MILLIS = 5_000L
    }
}
