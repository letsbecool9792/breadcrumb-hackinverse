package com.lbc.breadcrumb.capture

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ui.capture.CaptureSheet
import com.lbc.breadcrumb.ui.capture.CaptureState
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ClipboardCaptureActivity : ComponentActivity() {

    private var timeoutJob: Job? = null
    private var hasReadClipboard = false
    private val stateFlow = MutableStateFlow<CaptureState>(CaptureState.Saving)
    private val heldIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as BreadcrumbApp
        setContent {
            val state by stateFlow.collectAsState()

            BreadcrumbTheme {
                CaptureSheet(
                    state = state,
                    onUndo = {
                        if (state is CaptureState.Saved) {
                            val savedMemories = (state as CaptureState.Saved).memories
                            app.applicationScope.launch {
                                savedMemories.forEach { mem ->
                                    app.database.memoryDao().deleteEverywhere(mem, System.currentTimeMillis())
                                    app.originals.delete(mem)
                                }
                            }
                            stateFlow.value = CaptureState.Removed
                        }
                    },
                    onDismiss = { releaseAndClose(app) },
                    onNote = { note ->
                        val saved = (stateFlow.value as? CaptureState.Saved)?.memories ?: return@CaptureSheet
                        app.applicationScope.launch {
                            app.database.captureDao().setNote(saved.map { it.id }, note)
                        }
                    }
                )
            }
        }

        // Give it a 2s timeout so a window that never gains focus cannot sit invisibly
        timeoutJob = lifecycleScope.launch {
            delay(2000)
            if (!hasReadClipboard) {
                releaseAndClose(app)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasReadClipboard) {
            hasReadClipboard = true
            timeoutJob?.cancel()
            readClipboardAndSave()
        }
    }

    private fun readClipboardAndSave() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip == null || clip.itemCount == 0) {
            stateFlow.value = CaptureState.Failed(getString(R.string.capture_nothing_to_save))
            return
        }

        val isSensitive = clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") ?: false
        if (isSensitive) {
            stateFlow.value = CaptureState.Failed(getString(R.string.capture_nothing_to_save))
            return
        }

        val item = clip.getItemAt(0)
        val uri = item.uri
        val text = item.text?.toString()

        val app = application as BreadcrumbApp

        if (uri != null) {
            lifecycleScope.launch {
                val mimeType = contentResolver.getType(uri) ?: ""
                val isPdf = mimeType == "application/pdf" || mimeType.endsWith("pdf")
                val extension = if (isPdf) "pdf" else mimeType.substringAfterLast('/', "jpg")
                val id = UUID.randomUUID().toString()
                
                try {
                    val (file, title, createdAt) = withContext(Dispatchers.IO) {
                        val copiedFile = app.originals.copyIn(uri, id, extension)
                        val extractedTitle = if (isPdf) MediaHelper.getDisplayName(this@ClipboardCaptureActivity, uri) else null
                        val extractedDate = if (!isPdf) MediaHelper.getDateTaken(this@ClipboardCaptureActivity, uri) else null
                        Triple(copiedFile, extractedTitle, extractedDate)
                    }

                    val memory = Memory(
                        id = id,
                        type = if (isPdf) MemoryType.PDF else MemoryType.IMAGE,
                        title = title,
                        contentCreatedAt = createdAt,
                        localUri = app.originals.uriFor(file)
                    )

                    heldIds.add(memory.id)
                    app.uploadHolds.addAll(heldIds)

                    app.applicationScope.launch {
                        app.database.memoryDao().upsert(memory)
                    }
                    stateFlow.value = CaptureState.Saved(1, listOf(memory))
                } catch (e: Exception) {
                    e.printStackTrace()
                    stateFlow.value = CaptureState.Failed(getString(R.string.capture_nothing_to_save))
                }
            }
        } else if (!text.isNullOrBlank()) {
            val (type, hasLink) = TextRules.parseText(text)
            val memory = Memory(
                type = type,
                hasLink = hasLink,
                rawText = text
            )

            heldIds.add(memory.id)
            app.uploadHolds.addAll(heldIds)

            app.applicationScope.launch {
                app.database.memoryDao().upsert(memory)
            }
            stateFlow.value = CaptureState.Saved(1, listOf(memory))
        } else {
            stateFlow.value = CaptureState.Failed(getString(R.string.capture_nothing_to_save))
        }
    }

    override fun onStop() {
        super.onStop()
        releaseHolds()
    }

    override fun onDestroy() {
        releaseHolds()
        super.onDestroy()
    }

    private fun releaseHolds() {
        val app = application as BreadcrumbApp
        app.uploadHolds.removeAll(heldIds)
        heldIds.clear()
    }

    private fun releaseAndClose(app: BreadcrumbApp) {
        app.uploadHolds.removeAll(heldIds)
        heldIds.clear()
        closeCleanly()
    }

    private fun closeCleanly() {
        val decorView = window.decorView
        decorView.visibility = View.INVISIBLE
        decorView.postOnAnimation {
            decorView.postOnAnimation {
                finish()
            }
        }
    }
}
