package com.lbc.breadcrumb.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * App-private home for the originals of saved images and PDFs (architecture
 * rule 1: originals stay on the device).
 *
 * Lives under filesDir rather than cacheDir -- the system is free to evict
 * cache, and an original that silently disappears is a memory that silently
 * disappears.
 */
class OriginalStore(
    private val context: Context,
    private val root: File = File(context.filesDir, "originals"),
) {

    /**
     * Copies a shared file in and returns the stored file.
     *
     * The file is named by the memory id, never by the sender's display name:
     * a display name comes from another app's provider and could contain path
     * segments. Writes go to a .part file that is renamed on success, so a
     * failed or interrupted copy can never leave a truncated original behind.
     *
     * Must run while the share's read grant is still held -- that is, before
     * the receiving activity finishes.
     */
    fun copyIn(source: Uri, id: String, extension: String): File {
        root.mkdirs()
        val target = File(root, "$id.$extension")
        val partial = File(root, "$id.$extension.part")

        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("provider returned no stream for $source")
            input.use { src -> partial.outputStream().use { dst -> src.copyTo(dst) } }

            if (!partial.renameTo(target)) throw IOException("could not finalise $target")
            return target
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    /**
     * Moves a file already in the app's own storage, such as the importer's
     * staging copy, into the store as [id].[extension], and returns it. A rename
     * where it can (cacheDir and filesDir share a volume); otherwise a copy
     * through a .part file, as [copyIn], so a failure never leaves a truncated
     * original behind.
     */
    fun moveIn(file: File, id: String, extension: String): File {
        root.mkdirs()
        val target = File(root, "$id.$extension")
        if (file.renameTo(target)) return target

        val partial = File(root, "$id.$extension.part")
        try {
            file.inputStream().use { src -> partial.outputStream().use { dst -> src.copyTo(dst) } }
            if (!partial.renameTo(target)) throw IOException("could not finalise $target")
            file.delete()
            return target
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    fun uriFor(file: File): String = Uri.fromFile(file).toString()

    /**
     * Resolves a memory's stored original, or null when it has none, the file
     * is gone, or its localUri points anywhere outside this store. The last
     * case matters: this is also what [delete] trusts, so a row with a
     * hand-edited or sample localUri can never make us delete a foreign file.
     */
    fun fileFor(memory: Memory): File? {
        val uri = memory.localUri?.let(Uri::parse) ?: return null
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null).canonicalFile
        val base = root.canonicalFile
        if (file.parentFile != base) return null
        return file.takeIf { it.isFile }
    }

    /**
     * Every original held. For the importer, which compares what it has just
     * copied against them so the same bytes are never saved twice. A .part
     * file is a copy still being made, or one that died halfway.
     */
    fun files(): List<File> =
        root.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()

    fun delete(memory: Memory) {
        fileFor(memory)?.delete()
    }

    fun clear() {
        root.listFiles()?.forEach { it.delete() }
    }
}
