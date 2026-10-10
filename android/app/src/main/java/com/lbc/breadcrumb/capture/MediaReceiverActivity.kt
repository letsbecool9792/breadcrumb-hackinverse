package com.lbc.breadcrumb.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ui.capture.CaptureSheet
import com.lbc.breadcrumb.ui.capture.CaptureState
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MediaReceiverActivity : ComponentActivity() {

    private val heldIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        }

        val stateFlow = MutableStateFlow<CaptureState>(CaptureState.Saving)
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
                    onDismiss = { releaseAndFinish(app) },
                    onNote = { note ->
                        val saved = (stateFlow.value as? CaptureState.Saved)?.memories ?: return@CaptureSheet
                        app.applicationScope.launch {
                            saved.forEach { mem ->
                                val updated = mem.copy(note = note, updatedAt = System.currentTimeMillis())
                                app.database.memoryDao().update(updated)
                            }
                        }
                    }
                )
            }
        }

        if (uris.isEmpty()) {
            stateFlow.value = CaptureState.Failed(getString(R.string.capture_nothing_to_save))
            return
        }

        val caption = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val hasLink = caption != null && TextRules.parseText(caption).second
        val (sourceApp, sourceAppLabel) = SourceHelper.getSource(this)

        lifecycleScope.launch {
            val savedMemories = mutableListOf<Memory>()
            for (uri in uris) {
                val mimeType = contentResolver.getType(uri) ?: ""
                val isPdf = mimeType == "application/pdf" || mimeType.endsWith("pdf")
                val extension = if (isPdf) "pdf" else mimeType.substringAfterLast('/', "jpg")

                val id = UUID.randomUUID().toString()
                try {
                    val (file, title, createdAt) = withContext(Dispatchers.IO) {
                        val copiedFile = app.originals.copyIn(uri, id, extension)
                        val extractedTitle = if (isPdf) MediaHelper.getDisplayName(this@MediaReceiverActivity, uri) else null
                        val extractedDate = if (!isPdf) MediaHelper.getDateTaken(this@MediaReceiverActivity, uri) else null
                        Triple(copiedFile, extractedTitle, extractedDate)
                    }

                    val memory = Memory(
                        id = id,
                        type = if (isPdf) MemoryType.PDF else MemoryType.IMAGE,
                        hasLink = hasLink,
                        rawText = caption,
                        title = title,
                        contentCreatedAt = createdAt,
                        sourceApp = sourceApp,
                        sourceAppLabel = sourceAppLabel,
                        localUri = app.originals.uriFor(file)
                    )

                    savedMemories.add(memory)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (savedMemories.isNotEmpty()) {
                // Hold all ids from upload
                heldIds.addAll(savedMemories.map { it.id })
                app.uploadHolds.addAll(heldIds)

                app.applicationScope.launch {
                    app.database.memoryDao().upsertAll(savedMemories)
                }
                stateFlow.value = CaptureState.Saved(savedMemories.size, savedMemories)
            } else {
                stateFlow.value = CaptureState.Failed(getString(R.string.capture_nothing_to_save))
            }
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

    private fun releaseAndFinish(app: BreadcrumbApp) {
        app.uploadHolds.removeAll(heldIds)
        heldIds.clear()
        closeCleanly()
    }

    private fun closeCleanly() {
        val decorView = window.decorView
        decorView.visibility = android.view.View.INVISIBLE
        decorView.postOnAnimation {
            decorView.postOnAnimation {
                finish()
            }
        }
    }
}
