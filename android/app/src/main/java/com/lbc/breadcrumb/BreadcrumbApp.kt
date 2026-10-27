package com.lbc.breadcrumb

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.data.SpokenNote
import com.lbc.breadcrumb.embed.EmbedWorker
import com.lbc.breadcrumb.embed.Embedder
import com.lbc.breadcrumb.embed.ModelDownloadWorker
import com.lbc.breadcrumb.embed.ModelStatus
import com.lbc.breadcrumb.embed.ModelStore
import com.lbc.breadcrumb.embed.ModelWatch
import com.lbc.breadcrumb.imports.BulkImporter
import com.lbc.breadcrumb.ocr.LinkWorker
import com.lbc.breadcrumb.ocr.OcrQueue
import com.lbc.breadcrumb.ocr.workScheduler
import com.lbc.breadcrumb.voice.ListenQueue
import com.lbc.breadcrumb.voice.OnDeviceTranscriber
import com.lbc.breadcrumb.voice.Transcriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BreadcrumbApp : Application() {

    /**
     * Work that must outlive the Activity that started it.
     *
     * Capture surfaces finish within milliseconds of launching -- that is the
     * point of them -- so a `lifecycleScope` coroutine would be cancelled
     * before the insert ever reached Room. SupervisorJob so one failed save
     * cannot take the scope down with it.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The one database. Built on first use, never on the main thread at a cold start. */
    val database: BreadcrumbDatabase get() = BreadcrumbDatabase.get(this)

    /** Where the originals of saved pictures and PDFs live (architecture rule 1). */
    val originals: OriginalStore by lazy { OriginalStore(this) }

    /** The embedding model's file, downloaded once (local-first L1). */
    val models: ModelStore by lazy { ModelStore(this) }

    /** Where the model is, for the first-run screen and the model's bar (local-first L8). */
    val modelStatus: StateFlow<ModelStatus> by lazy {
        val ready = models.ready() != null
        ModelWatch(this, models).status.stateIn(
            applicationScope,
            SharingStarted.WhileSubscribed(5_000),
            if (ready) ModelStatus.Ready else ModelStatus.Waiting(models.downloaded(), ModelStatus.Why.STARTING),
        )
    }

    /** EmbeddingGemma 2 on the phone: memories are embedded, and searched, here (local-first L1). */
    val embedder: Embedder by lazy { Embedder(models::ready, cacheDir, applicationScope) }

    /** Bulk importer for bringing in content already on the phone (reading R5). */
    val importer: BulkImporter by lazy { BulkImporter(this) }

    /**
     * Reading workstream: true while the OCR queue is working through unread
     * pictures and PDFs. Presumed true until the queue has looked, and goes
     * false in a finally when the queue finishes or never starts. The upload
     * pass held unread pictures back on it; since local-first L7 nothing reads it.
     */
    val readingOriginals = MutableStateFlow(true)

    /**
     * How many times one of the app's activities has come to the front. The
     * listen queue tries a voice note that had to wait again then: the phone
     * may lend its speech recognizer only to the app on screen.
     */
    val inFront = MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        // Before the OCR queue starts: a read landing first would call the
        // no-op, and nothing would be scheduled.
        workScheduler = {
            LinkWorker.schedule(it)
            // What was saved or read is embedded on the phone (local-first L1).
            EmbedWorker.schedule(it)
        }
        // Work that runs in every process starts here, inside applicationScope
        // so none of it touches the main thread during a capture's cold start:
        // the OCR queue (reading workstream), and the first schedule of links'
        // pages and embedding, for whatever a killed process left. Building the
        // queue builds the database.
        OcrQueue.launchIn(applicationScope, readingOriginals) { OcrQueue(this) }
        // Voice notes are heard on the phone, as they come and whenever the app comes to the front.
        registerActivityLifecycleCallbacks(FrontCounter(inFront))
        // One at a time: the recognizer serves one session.
        val hearing = Mutex()
        val phone = OnDeviceTranscriber(this)
        val transcriber = Transcriber { file -> hearing.withLock { phone.transcribe(file) } }
        applicationScope.launch {
            val reading = database.readingDao()
            ListenQueue(
                unheard = reading.observeUnheard(),
                wakes = inFront,
                newest = reading::getNewestUnheard,
                idOf = Memory::id,
                fileOf = originals::fileFor,
                write = { id, words -> reading.updateExtractedText(id, words) },
                transcriber = transcriber,
                scheduleWork = { workScheduler(this@BreadcrumbApp) },
            ).run()
        }
        // And notes said over a memory, into the memory's note.
        applicationScope.launch {
            val spoken = database.spokenNoteDao()
            ListenQueue(
                unheard = spoken.observeUnheard(),
                wakes = inFront,
                newest = spoken::getNewestUnheard,
                idOf = SpokenNote::memoryId,
                fileOf = { note -> originals.spokenFile(note.memoryId)?.takeIf { it.isFile } },
                write = { id, words -> spoken.heard(id, words) },
                transcriber = transcriber,
                scheduleWork = { workScheduler(this@BreadcrumbApp) },
            ).run()
        }
        applicationScope.launch {
            LinkWorker.schedule(this@BreadcrumbApp)
            EmbedWorker.schedule(this@BreadcrumbApp)
            // From the first launch, not the first save: search by meaning waits for it (L8).
            if (models.ready() == null) ModelDownloadWorker.schedule(this@BreadcrumbApp)
        }
    }
}

/** Counts activities coming to the front ([BreadcrumbApp.inFront]). */
private class FrontCounter(private val count: MutableStateFlow<Int>) : Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) {
        count.value++
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
