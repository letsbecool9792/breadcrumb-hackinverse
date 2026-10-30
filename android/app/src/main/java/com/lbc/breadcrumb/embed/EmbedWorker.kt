package com.lbc.breadcrumb.embed

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lbc.breadcrumb.BreadcrumbApp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Embeds memories on the phone (local-first L1): runs [EmbedPass] after a save
 * or a read, again while new work keeps arriving. Pictures go with the rest,
 * charging or not (roadmap 6). Unique work, so one runs at a time.
 */
class EmbedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BreadcrumbApp
        val pass = EmbedPass(
            memories = { app.database.memoryDao().getAll() },
            stored = app.database.embeddingDao(),
            embeddings = app.embedder,
            pictureOf = { memory -> app.originals.fileFor(memory)?.let(Pictures::jpeg) },
            hasPicture = { memory -> app.originals.fileFor(memory) != null },
        )
        var pictures = 0
        val outcome = passes.withLock {
            var outcome: EmbedPass.Outcome
            do {
                val asked = requests.get()
                outcome = pass.run()
                if (outcome is EmbedPass.Outcome.Done) pictures += outcome.pictures
                // Asked again while it ran, by a save or a read after it had looked: one more.
            } while (outcome is EmbedPass.Outcome.Done && requests.get() != asked)
            outcome
        }
        // A picture takes the model to about 0.8 GB; nothing on the phone should wait two minutes for that back.
        if (pictures > 0) app.embedder.close()
        if (outcome == EmbedPass.Outcome.NoModel) ModelDownloadWorker.schedule(app)
        return Result.success()
    }

    companion object {
        private const val NAME = "embed"

        /** Schedules since the process started, so a run can tell it was asked again meanwhile. */
        private val requests = AtomicLong()

        /** One pass at a time in the process. */
        private val passes = Mutex()

        /** There is something new to embed: called after a save or a read. */
        fun schedule(context: Context) {
            requests.incrementAndGet()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<EmbedWorker>().build())
        }
    }
}

/**
 * Downloads the embedding model once, then embeds what is waiting: on an
 * unmetered network, unless the person asked for it on mobile data from the
 * first-run screen.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BreadcrumbApp
        val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
        val ready = try {
            app.models.download(client)
        } catch (_: java.io.IOException) {
            false
        }
        if (!ready) return Result.retry()
        EmbedWorker.schedule(app)
        return Result.success()
    }

    companion object {
        private const val NAME = "embedding-model"

        /**
         * Waits for Wi-Fi; [anyNetwork] (asked for on mobile data) takes the place of a download
         * still waiting for it. Only ever offered while it waits, so nothing running is replaced.
         */
        fun schedule(context: Context, anyNetwork: Boolean = false) {
            val network = if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
                .build()
            val policy = if (anyNetwork) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }
    }
}
