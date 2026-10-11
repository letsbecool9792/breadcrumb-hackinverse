package com.lbc.breadcrumb.embed

import android.content.Context
import kotlinx.coroutines.Dispatchers
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

    /** The model, when a whole one is on the phone. */
    fun ready(): File? = listOfNotNull(file, pushed).firstOrNull { it.isFile && it.length() == SIZE }

    /**
     * Downloads the model, carrying on from a partial download, and keeps it
     * only when its hash matches. True when it is ready.
     */
    suspend fun download(client: OkHttpClient): Boolean = withContext(Dispatchers.IO) {
        if (ready() != null) return@withContext true
        val part = File(file.parentFile, "$FILE_NAME.part").apply { parentFile?.mkdirs() }
        val have = part.length()
        val request = Request.Builder().url(URL).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            // 206 carries on from what we have; anything else is the whole file again.
            val append = response.code == 206
            FileOutputStream(part, append).use { out -> response.body.byteStream().copyTo(out) }
        }
        if (part.length() != SIZE || sha256(part) != SHA256) {
            part.delete()
            return@withContext false
        }
        part.renameTo(file)
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
        private const val SHA256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0"
    }
}
