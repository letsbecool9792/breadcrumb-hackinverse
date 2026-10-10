package com.lbc.breadcrumb.capture

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.ui.capture.CaptureSheet
import com.lbc.breadcrumb.ui.capture.CaptureState
import com.lbc.breadcrumb.ui.theme.BreadcrumbTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    private val heldIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as BreadcrumbApp
        
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        
        if (text.isNullOrBlank()) {
            setContent {
                BreadcrumbTheme(darkTheme = true) {
                    CaptureSheet(
                        state = CaptureState.Failed(getString(com.lbc.breadcrumb.R.string.capture_nothing_to_save)),
                        onUndo = {},
                        onDismiss = { closeCleanly() }
                    )
                }
            }
            return
        }

        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
        val cleanTitle = TextRules.titleFor(subject, text)
        val (type, hasLink) = TextRules.parseText(text)
        val (sourceApp, sourceAppLabel) = SourceHelper.getSource(this)
        
        val memory = Memory(
            type = type,
            hasLink = hasLink,
            rawText = text,
            title = cleanTitle,
            sourceApp = sourceApp,
            sourceAppLabel = sourceAppLabel
        )

        // Hold back from upload while the sheet is open
        heldIds.add(memory.id)
        app.uploadHolds.addAll(heldIds)

        val stateFlow = MutableStateFlow<CaptureState>(CaptureState.Saving)

        setContent {
            val state by stateFlow.collectAsState()
            BreadcrumbTheme(darkTheme = true) {
                CaptureSheet(
                    state = state,
                    onUndo = {
                        app.applicationScope.launch {
                            app.database.memoryDao().deleteEverywhere(memory, System.currentTimeMillis())
                        }
                        stateFlow.value = CaptureState.Removed
                    },
                    onDismiss = { releaseAndClose(app) },
                    onNote = { note ->
                        app.applicationScope.launch {
                            app.database.captureDao().setNote(listOf(memory.id), note)
                        }
                    }
                )
            }
        }

        app.applicationScope.launch {
            app.database.memoryDao().upsert(memory)
            stateFlow.value = CaptureState.Saved(1, listOf(memory))
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
