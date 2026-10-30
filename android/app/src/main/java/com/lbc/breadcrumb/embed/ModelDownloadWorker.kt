package com.lbc.breadcrumb.embed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.MainActivity
import com.lbc.breadcrumb.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Downloads the embedding model once, then embeds what is waiting: on an
 * unmetered network, unless the person asked for it on mobile data from the
 * first-run screen.
 *
 * A foreground service while it runs, its progress in a notification, so
 * leaving the app or swiping it away doesn't stop it: as plain background
 * work, the system cuts a job off after about ten minutes, and some phones far
 * sooner once the app is closed. Android lets an app become one only while it
 * is on screen (12+), so a download that starts in the background (Wi-Fi
 * arriving later) runs as plain work, and each try carries on from where the
 * last one stopped.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BreadcrumbApp
        val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
        val ready = coroutineScope {
            val shown = launch { showProgress(app) }
            try {
                app.models.download(client)
            } catch (_: IOException) {
                false
            } finally {
                shown.cancel()
            }
        }
        if (!ready) return Result.retry()
        EmbedWorker.schedule(app)
        return Result.success()
    }

    /** The notification, kept in step with the download a percent at a time. */
    private suspend fun showProgress(app: BreadcrumbApp) {
        var shown = -1
        app.models.progress.collect { progress ->
            val bytes = when (progress) {
                is ModelStore.Progress.Downloading -> progress.bytes
                ModelStore.Progress.Checking -> ModelStore.SIZE
                ModelStore.Progress.Idle -> app.models.downloaded()
            }
            val percent = (bytes * 100 / ModelStore.SIZE).toInt()
            if (percent != shown) {
                shown = percent
                inFront(app, bytes)
            }
        }
    }

    private suspend fun inFront(context: Context, bytes: Long) {
        try {
            setForeground(ForegroundInfo(NOTIFICATION, notification(context, bytes), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // In the background Android refuses a foreground service (12+): it goes on as plain work.
        }
    }

    /** "Getting search ready", "142 of 388 MB" and the bar; a tap opens the app. */
    private fun notification(context: Context, bytes: Long): Notification {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.model_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_crumb)
            .setContentTitle(context.getString(R.string.first_run_title))
            .setContentText(ModelStatus.amount(ModelStatus.Downloading(bytes)))
            .setProgress(100, (bytes * 100 / ModelStore.SIZE).toInt(), false)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NAME = "embedding-model"
        private const val CHANNEL = "model"
        private const val NOTIFICATION = 1

        /**
         * Waits for Wi-Fi; [anyNetwork] (asked for on mobile data) takes the place of a download
         * still waiting for it, and is remembered, so the first-run screen doesn't offer it again.
         * Only ever offered while it waits, so nothing running is replaced.
         */
        fun schedule(context: Context, anyNetwork: Boolean = false) {
            if (anyNetwork) prefs(context).edit().putBoolean(ANY_NETWORK, true).apply()
            val network = if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
                .build()
            val policy = if (anyNetwork) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }

        /** Whether the person asked for the model on mobile data. */
        fun onAnyNetwork(context: Context): Boolean = prefs(context).getBoolean(ANY_NETWORK, false)

        private const val ANY_NETWORK = "any_network"

        private fun prefs(context: Context) = context.getSharedPreferences("model", Context.MODE_PRIVATE)
    }
}
