package com.lbc.breadcrumb.ocr

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.embed.EmbedWorker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Reads unread links' pages ([LinkReading], reading R4) once there is a
 * network, then has what they say embedded. Unique work, so one runs at a
 * time, and again while new saves keep arriving. A link saved offline waits
 * for the next save, or the next start, with a network.
 *
 * Until local-first L7 the upload pass read them, on its way to the server.
 */
class LinkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BreadcrumbApp
        passes.withLock {
            do {
                val asked = requests.get()
                LinkReading(app).readAllPending()
                // Asked again while it read, by a save after it had looked: one more.
            } while (requests.get() != asked)
        }
        // A page read changes what a link is embedded from.
        EmbedWorker.schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "links"

        /** Schedules since the process started, so a run can tell it was asked again meanwhile. */
        private val requests = AtomicLong()

        /** One run at a time in the process, even while a stopped run is still unwinding. */
        private val passes = Mutex()

        /** There may be a link to read: after every save. Keeps a run already waiting. */
        fun schedule(context: Context) {
            requests.incrementAndGet()
            val request = OneTimeWorkRequestBuilder<LinkWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
