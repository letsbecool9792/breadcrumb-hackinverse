package com.lbc.breadcrumb.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.embed.EmbedWorker
import com.lbc.breadcrumb.ocr.LinkReading
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * The upload queue: runs [UploadPass] when there is a network, again while
 * new work keeps arriving, and with backoff when the server says later.
 * Unique work, so one runs at a time.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BreadcrumbApp
        val pass = UploadPass(
            dao = app.database.uploadDao(),
            api = app.server,
            held = { app.uploadHolds.toList() },
            reading = { app.readingOriginals.value },
            pictureOf = { memory -> app.originals.fileFor(memory)?.let(Pictures::jpeg) },
            readLinks = { LinkReading(app).readAllPending() },
        )
        val outcome = passes.withLock {
            var outcome: UploadPass.Outcome
            do {
                val asked = requests.get()
                outcome = pass.run()
                // Asked again while it ran, by a save or a read after a step had looked: one more.
            } while (outcome is UploadPass.Outcome.Done && requests.get() != asked)
            outcome
        }
        // The pass reads links' pages, and a page read changes what a link is embedded from.
        EmbedWorker.schedule(applicationContext)
        return when (outcome) {
            is UploadPass.Outcome.Done -> {
                outcome.recheckAt?.let { recheckAt(applicationContext, it - System.currentTimeMillis()) }
                Result.success()
            }
            is UploadPass.Outcome.Later -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "upload"
        private const val RECHECK = "upload-recheck"

        /** Schedules since the process started, so a run can tell it was asked again meanwhile. */
        private val requests = AtomicLong()

        /** One pass at a time in the process, even while a stopped run is still unwinding. */
        private val passes = Mutex()

        /**
         * There is something new to send: capture calls this after a save,
         * reading after a read. Keeps a run already waiting, rather than
         * replacing it: a run in backoff stays in backoff.
         */
        fun schedule(context: Context) {
            requests.incrementAndGet()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request())
        }

        /** The debug list's "sync now": a run at once, out of any backoff. */
        fun runNow(context: Context) {
            requests.incrementAndGet()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request())
        }

        private fun request() = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()

        /**
         * Schedules a run after [delayMillis], through [UploadRecheck]: never
         * this work itself, which a pending recheck would hold back behind its
         * delay, keeping a fresh save waiting.
         */
        private fun recheckAt(context: Context, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<UploadRecheck>()
                .setInitialDelay(Duration.ofMillis(delayMillis.coerceAtLeast(0)))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(RECHECK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Schedules the upload queue once an unread picture's grace is over: a read that failed tells nobody. */
class UploadRecheck(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        UploadWorker.schedule(applicationContext)
        return Result.success()
    }
}
