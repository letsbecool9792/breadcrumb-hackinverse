package com.lbc.breadcrumb.embed

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Where the embedding model lives on the phone. Never in the APK: it is
 * downloaded once into app storage, and checked against the published hash.
 * During development `adb push` can put it in the app's external files
 * directory instead, where it is used as it is.
 */
class ModelStore(private val context: Context) {

    val file: File get() = File(File(context.filesDir, "models"), FILE_NAME)

    private val pushed: File? get() = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }

    private val part: File get() = File(file.parentFile, "$FILE_NAME.part")

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)

    /** What a download in this process is doing, for the first-run screen and the model's bar. */
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    sealed interface Progress {
        data object Idle : Progress
        data class Downloading(val bytes: Long) : Progress

        /** The whole file is here, and its hash is being checked. */
        data object Checking : Progress
    }

    /** How much of the model a download has brought so far, kept across runs. */
    fun downloaded(): Long = part.length()

    /** The model, when a whole one is on the phone. */
    fun ready(): File? = listOfNotNull(file, pushed).firstOrNull { it.isFile && it.length() == SIZE }

    /**
     * Downloads the model, carrying on from a partial download, and keeps it
     * only when its hash matches. True when it is ready.
     */
    suspend fun download(client: OkHttpClient): Boolean = withContext(Dispatchers.IO) {
        if (ready() != null) return@withContext true
        val part = part.apply { parentFile?.mkdirs() }
        try {
            val have = part.length()
            val request = Request.Builder().url(URL).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                // 206 carries on from what we have; anything else is the whole file again.
                val append = response.code == 206
                var written = if (append) have else 0L
                _progress.value = Progress.Downloading(written)
                FileOutputStream(part, append).use { out ->
                    val input = response.body.byteStream()
                    val buffer = ByteArray(1 shl 16)
                    var shown = written
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        if (written - shown >= PROGRESS_STEP) {
                            shown = written
                            _progress.value = Progress.Downloading(written)
                        }
                    }
                }
            }
            _progress.value = Progress.Checking
            if (part.length() != SIZE || sha256(part) != SHA256) {
                part.delete()
                return@withContext false
            }
            part.renameTo(file)
        } finally {
            _progress.value = Progress.Idle
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FILE_NAME = "${EmbedText.MODEL}.litertlm"

        /** Pinned to one revision of the LiteRT Community repo (Apache 2.0, not gated). */
        private const val URL = "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/" +
            "resolve/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/$FILE_NAME"
        const val SIZE = 387_710_976L

        /** The bar moves a megabyte at a time. */
        private const val PROGRESS_STEP = 1L shl 20
        private const val SHA256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0"
    }
}
