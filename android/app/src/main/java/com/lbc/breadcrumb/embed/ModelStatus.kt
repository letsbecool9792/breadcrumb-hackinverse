package com.lbc.breadcrumb.embed

import java.util.Locale

/**
 * Where the embedding model is, as the first-run screen and the model's bar
 * say it (design §19). Pure, so the JVM tests the words.
 */
sealed interface ModelStatus {
    data object Ready : ModelStatus

    data class Downloading(val bytes: Long) : ModelStatus

    /** The whole file is here, and its hash is being checked. */
    data object Checking : ModelStatus

    /** Not downloading: [bytes] so far, and [why]. */
    data class Waiting(val bytes: Long, val why: Why) : ModelStatus

    enum class Why {
        /** On mobile data: the model waits for Wi-Fi unless the person says otherwise. */
        WIFI,
        NO_INTERNET,

        /** Online and unmetered: the download is about to start, or start again. */
        STARTING,
    }

    companion object {
        const val TOTAL = ModelStore.SIZE

        fun of(ready: Boolean, progress: ModelStore.Progress, downloaded: Long, online: Boolean, unmetered: Boolean): ModelStatus =
            when {
                ready -> Ready
                progress is ModelStore.Progress.Downloading -> Downloading(progress.bytes)
                progress == ModelStore.Progress.Checking -> Checking
                !online -> Waiting(downloaded, Why.NO_INTERNET)
                !unmetered -> Waiting(downloaded, Why.WIFI)
                else -> Waiting(downloaded, Why.STARTING)
            }

        /** How far: 0 to 1. */
        fun fraction(status: ModelStatus): Float = when (status) {
            Ready, Checking -> 1f
            is Downloading -> (status.bytes.toFloat() / TOTAL).coerceIn(0f, 1f)
            is Waiting -> (status.bytes.toFloat() / TOTAL).coerceIn(0f, 1f)
        }

        /** "142 of 388 MB", in megabytes of a million bytes, as the phone counts them. */
        fun amount(status: ModelStatus): String {
            val bytes = when (status) {
                Ready, Checking -> TOTAL
                is Downloading -> status.bytes
                is Waiting -> status.bytes
            }
            return "${mb(bytes)} of ${mb(TOTAL)} MB"
        }

        /** The first-run screen's line under the bar. */
        fun state(status: ModelStatus): String = when (status) {
            Ready -> "ready"
            is Downloading -> "downloading"
            Checking -> "checking it"
            is Waiting -> when (status.why) {
                Why.WIFI -> "waiting for wi-fi"
                Why.NO_INTERNET -> "waiting for the internet"
                Why.STARTING -> "starting"
            }
        }

        /** The bar over the search field; null once the model is ready. */
        fun bar(status: ModelStatus): String? = when (status) {
            Ready -> null
            is Downloading -> "getting search ready · ${percent(status.bytes)}%"
            Checking -> "getting search ready · checking it"
            is Waiting -> when (status.why) {
                Why.WIFI -> "search by meaning waits for wi-fi"
                Why.NO_INTERNET -> "search by meaning waits for the internet"
                Why.STARTING -> "getting search ready…"
            }
        }

        private fun percent(bytes: Long): Int = (bytes * 100 / TOTAL).toInt().coerceIn(0, 99)

        private fun mb(bytes: Long): String = String.format(Locale.ROOT, "%d", (bytes + 500_000) / 1_000_000)
    }
}
