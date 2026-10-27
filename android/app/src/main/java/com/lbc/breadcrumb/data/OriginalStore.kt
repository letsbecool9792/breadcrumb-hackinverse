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
     * Resolves a memory's stored original by its file's name in this store, or
     * null when it has none, the file is gone, or the name could reach outside
     * the store. By name, not by the absolute path in localUri: that path names
     * the package, so a restore into another package, or a new applicationId,
     * would lose every original. Only a file directly in this store is ever
     * found, and this is also what [delete] trusts, so a hand-edited localUri
     * can never make us delete a foreign file.
     */
    fun fileFor(memory: Memory): File? {
        val uri = memory.localUri?.let(Uri::parse) ?: return null
        if (uri.scheme != "file") return null
        return named(File(uri.path ?: return null).name)?.takeIf { it.isFile }
    }

    /**
     * The file called [name] directly in this store, whether or not it exists
     * yet; null when the name could reach outside it.
     */
    fun named(name: String): File? {
        if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\\' in name) return null
        val file = File(root, name)
        if (file.canonicalFile.parentFile != root.canonicalFile) return null
        return file
    }

    /**
     * Where the recording of a note said over [memoryId] lives ([SpokenNote]),
     * named by the memory, so it goes with the memory's own original in
     * [delete] without asking the database.
     */
    fun spokenFile(memoryId: String): File? = named("$memoryId$SPOKEN")

    /**
     * Every original held. For the importer, which compares what it has just
     * copied against them so the same bytes are never saved twice. A .part
     * file is a copy still being made, or one that died halfway.
     */
    fun files(): List<File> =
        root.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()

    /** The memory's original, and the recording of a note said over it. */
    fun delete(memory: Memory) {
        fileFor(memory)?.delete()
        spokenFile(memory.id)?.delete()
    }

    fun clear() {
        root.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** The ending of a spoken note's recording: "<memory id>.spoken.m4a". */
        const val SPOKEN = ".spoken.m4a"
    }
}
