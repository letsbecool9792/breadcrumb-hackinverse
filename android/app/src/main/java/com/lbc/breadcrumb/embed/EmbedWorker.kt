package com.lbc.breadcrumb.embed

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.sync.Pictures
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Embeds memories on the phone (local-first L1): runs [EmbedPass] after a save
 * or a read, again while new work keeps arriving. Pictures OCR could barely
 * read are left to a run on the charger. Unique work, so one runs at a time.
 */
class EmbedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BreadcrumbApp
        val pictures = inputData.getBoolean(PICTURES, false) || charging(app)
        val pass = EmbedPass(
            memories = { app.database.memoryDao().getAll() },
            stored = app.database.embeddingDao(),
            embeddings = app.embedder,
            pictureOf = { memory -> app.originals.fileFor(memory)?.let(Pictures::jpeg) },
            hasPicture = { memory -> app.originals.fileFor(memory) != null },
            pictures = pictures,
        )
        val outcome = passes.withLock {
            var outcome: EmbedPass.Outcome
            do {
                val asked = requests.get()
                outcome = pass.run()
                // Asked again while it ran, by a save or a read after it had looked: one more.
            } while (outcome is EmbedPass.Outcome.Done && requests.get() != asked)
            outcome
        }
        // A picture takes the model to about 1 GB; nothing on the phone should wait two minutes for that back.
        if (pictures) app.embedder.close()
        when (outcome) {
            is EmbedPass.Outcome.Done -> if (outcome.picturesWaiting && !pictures) onCharger(app)
            EmbedPass.Outcome.NoModel -> ModelDownloadWorker.schedule(app)
        }
        return Result.success()
    }

    private fun charging(context: Context): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    companion object {
        private const val NAME = "embed"
        private const val ON_CHARGER = "embed-pictures"
        private const val PICTURES = "pictures"

        /** Schedules since the process started, so a run can tell it was asked again meanwhile. */
        private val requests = AtomicLong()

        /** One pass at a time in the process. */
        private val passes = Mutex()

        /** There is something new to embed: called with the upload schedule, after a save or a read. */
        fun schedule(context: Context) {
            requests.incrementAndGet()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<EmbedWorker>().build())
        }

        private fun onCharger(context: Context) {
            val request = OneTimeWorkRequestBuilder<EmbedWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .setInputData(workDataOf(PICTURES to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ON_CHARGER, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** Downloads the embedding model once, on an unmetered network, then embeds what is waiting. */
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

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
