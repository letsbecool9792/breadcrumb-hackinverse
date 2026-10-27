package com.lbc.breadcrumb.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.ocr.workScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything kept, out to one file the person keeps, and back in on a new
 * phone (roadmap 4). A backup is a zip: [BackupFormat.MANIFEST] first, then
 * every original and spoken note's recording under [BackupFormat.ORIGINALS].
 *
 * A restore only adds: a memory already on the phone (by id) is left as it is,
 * and nothing is ever replaced or deleted. Its files arrive first, and only
 * then are its rows written, all in one transaction: a backup that stops
 * halfway leaves nothing behind, files included.
 *
 * One at a time, on the application's scope; [progress] says how it goes.
 */
class Backups(
    private val context: Context,
    private val database: BreadcrumbDatabase,
    private val originals: OriginalStore,
    private val scope: CoroutineScope,
    private val scheduleWork: (Context) -> Unit = { workScheduler(it) },
) {
    private val _progress = MutableStateFlow<BackupProgress?>(null)
    val progress: StateFlow<BackupProgress?> = _progress.asStateFlow()

    private val oneAtATime = Mutex()

    /** Clears a finished backup's line; one still running keeps it. */
    fun forget() {
        _progress.update { if (it?.finished == true) null else it }
    }

    /** Saves everything to the file the person picked. */
    fun save(uri: Uri): Job = scope.launch(Dispatchers.IO) {
        oneAtATime.withLock {
            _progress.value = BackupProgress.Saving(0, 0)
            _progress.value = try {
                val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("couldn't open the file")
                val (memories, bytes) = stream.use { save(it) }
                BackupProgress.Saved(memories, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupProgress.Failed(restoring = false, reason = reasonOf(e))
            }
        }
    }

    /** Brings in the backup the person picked. */
    fun restore(uri: Uri): Job = scope.launch(Dispatchers.IO) {
        oneAtATime.withLock {
            _progress.value = BackupProgress.Restoring(0, 0)
            _progress.value = try {
                val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("couldn't open the file")
                val (added, already) = stream.use { restore(it) }
                if (added > 0) scheduleWork(context)
                BackupProgress.Restored(added, already)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupProgress.Failed(restoring = true, reason = reasonOf(e))
            }
        }
    }

    /** Writes everything to [out] as a backup: the memories in it, and the bytes written. */
    suspend fun save(out: OutputStream): Pair<Int, Long> {
        val memories = database.memoryDao().getAll()
        val spoken = database.spokenNoteDao().getAll()
        val files = memories.associate { it.id to originals.fileFor(it) }
        val spokenFiles = spoken.associate { it.memoryId to originals.spokenFile(it.memoryId)?.takeIf(File::isFile) }
        val manifest = BackupManifest(
            exportedAt = System.currentTimeMillis(),
            memories = memories.map { BackupFormat.memory(it, files[it.id]?.name) },
            embeddings = database.embeddingDao().getAll().map(BackupFormat::embedding),
            // A note said over a memory whose recording is gone has nothing to bring back.
            spokenNotes = spoken.filter { spokenFiles[it.memoryId] != null }.map(BackupFormat::spokenNote),
            importedSources = database.importDao().getAll().map(BackupFormat::source),
        )
        val all = files.values.filterNotNull() + spokenFiles.values.filterNotNull()
        val counted = Counted(out)
        ZipOutputStream(counted.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST))
            zip.write(BackupFormat.encode(manifest).toByteArray())
            zip.closeEntry()
            // Pictures, PDFs and sound are compressed already.
            zip.setLevel(Deflater.NO_COMPRESSION)
            _progress.value = BackupProgress.Saving(0, all.size)
            all.forEachIndexed { i, file ->
                // Deleted since it was listed: its memory comes back without it.
                val input = try {
                    file.inputStream()
                } catch (_: IOException) {
                    null
                }
                if (input != null) {
                    zip.putNextEntry(ZipEntry(BackupFormat.ORIGINALS + file.name))
                    input.use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                _progress.value = BackupProgress.Saving(i + 1, all.size)
            }
        }
        return memories.size to counted.count
    }

    /**
     * Adds what a backup holds that the phone doesn't: the memories added, and
     * those already here. Throws [NotABackup] for anything else, having added
     * nothing.
     */
    suspend fun restore(input: InputStream): Pair<Int, Int> {
        val written = mutableListOf<File>()
        try {
            ZipInputStream(input.buffered()).use { zip ->
                val first = zip.nextEntry ?: throw NotABackup()
                if (first.name != BackupFormat.MANIFEST) throw NotABackup()
                val manifest = BackupFormat.decode(zip.readBytes().decodeToString()) ?: throw NotABackup()

                val here = database.memoryDao().ids().toSet()
                val fresh = manifest.memories.filter { it.id !in here }
                val freshIds = fresh.map { it.id }.toSet()
                val spoken = manifest.spokenNotes.filter { it.memoryId in freshIds }
                // Only the files of memories coming in, by names the store accepts and holds
                // nothing under: a restore never writes over a file that is there.
                val wanted = (fresh.mapNotNull { it.original } + spoken.map { it.memoryId + OriginalStore.SPOKEN })
                    .filter { originals.named(it)?.exists() == false }
                    .toSet()

                val arrived = mutableSetOf<String>()
                _progress.value = BackupProgress.Restoring(0, wanted.size)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.removePrefix(BackupFormat.ORIGINALS)
                    if (entry.isDirectory || !entry.name.startsWith(BackupFormat.ORIGINALS)) continue
                    if (name !in wanted || name in arrived) continue
                    val target = originals.named(name) ?: continue
                    val part = File(target.path + ".part")
                    target.parentFile?.mkdirs()
                    written += part
                    part.outputStream().use { zip.copyTo(it) }
                    if (!part.renameTo(target)) throw IOException("couldn't keep $name")
                    written += target
                    arrived += name
                    _progress.value = BackupProgress.Restoring(arrived.size, wanted.size)
                }

                val memories = fresh.mapNotNull { backedUp ->
                    val localUri = backedUp.original?.takeIf { it in arrived }?.let { originals.uriFor(originals.named(it)!!) }
                    BackupFormat.memory(backedUp, localUri)
                }
                database.withTransaction {
                    database.memoryDao().upsertAll(memories)
                    val added = memories.map { it.id }.toSet()
                    manifest.embeddings.filter { it.memoryId in added }.mapNotNull(BackupFormat::embedding)
                        .forEach { database.embeddingDao().upsert(it) }
                    database.spokenNoteDao().upsertAll(
                        spoken.filter { it.memoryId in added && it.memoryId + OriginalStore.SPOKEN in arrived }
                            .map(BackupFormat::spokenNote),
                    )
                    database.importDao().recordAll(manifest.importedSources.map(BackupFormat::source))
                }
                return memories.size to manifest.memories.size - fresh.size
            }
        } catch (e: Throwable) {
            for (file in written) file.delete()
            throw e
        }
    }

    /** Not a backup: no manifest first, or one this app can't read. */
    class NotABackup : IOException("that isn't a breadcrumb backup")

    private fun reasonOf(e: Exception): String = when {
        e is NotABackup -> e.message!!
        e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true ->
            "the phone is out of space"
        e is java.util.zip.ZipException -> "that file is damaged"
        else -> "couldn't read or write the file"
    }

    /** Counts what goes through. */
    private class Counted(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }
}
