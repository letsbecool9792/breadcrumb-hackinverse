package com.lbc.breadcrumb.imports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.ImportDao
import com.lbc.breadcrumb.data.ImportedSource
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryDao
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.data.ReadingDao
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

sealed interface ImportProgress {
    data class Scanning(val message: String) : ImportProgress
    data class Running(
        val current: Int,
        val total: Int,
        val imported: Int,
        val skipped: Int,
        val currentItem: String,
    ) : ImportProgress
    data class Done(
        val total: Int,
        val imported: Int,
        val skipped: Int,
    ) : ImportProgress
    data class Failed(val reason: String) : ImportProgress
}

/**
 * Bulk importer bringing in content already on the phone (reading workstream R5).
 *
 * Supported sources:
 * - Whole folder (ACTION_OPEN_DOCUMENT_TREE, e.g. Pictures/Screenshots or DCIM)
 * - Photo picker (PickMultipleVisualMedia)
 * - PDF file picker (OpenMultipleDocuments)
 * - Netscape HTML bookmark exports (OpenDocument)
 *
 * Requirements:
 * - Through Android pickers only; no broad storage permissions.
 * - Persistable tree grants taken and released at completion (respecting the 512 grant cap).
 * - Individual files read while picker grants are live.
 * - Two-level deduplication: filename + size (before copying), then SHA-256 against existing originals.
 * - Deleted-after-import memories are never recreated on subsequent imports.
 * - Writes memory rows/originals and nothing else; OCR and reading queues handle extraction.
 * - Sets [readingOriginals] true while images are being imported so the upload queue holds them back.
 * - Oldest content first.
 */
class BulkImporter(
    private val context: Context,
    private val database: BreadcrumbDatabase = (context.applicationContext as BreadcrumbApp).database,
    private val memoryDao: MemoryDao = database.memoryDao(),
    private val importDao: ImportDao = database.importDao(),
    private val readingDao: ReadingDao = database.readingDao(),
    private val originals: OriginalStore = (context.applicationContext as BreadcrumbApp).originals,
    private val readingOriginals: MutableStateFlow<Boolean> = (context.applicationContext as BreadcrumbApp).readingOriginals,
    private val scope: CoroutineScope = (context.applicationContext as BreadcrumbApp).applicationScope,
) {

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    private val importMutex = Mutex()
    private var currentJob: Job? = null

    /**
     * Imports run one at a time, in the order they were asked for: two at once
     * would share one progress line, and could both import the same file
     * before either had recorded it.
     */
    private fun launchImport(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(Dispatchers.IO) { importMutex.withLock { block() } }
        currentJob = job
        return job
    }

    /**
     * Clears a finished or failed import progress (reading workstream R8).
     * If called while an import is in flight, cancels the active import job.
     * Never deletes existing saved memories.
     */
    fun forget() {
        currentJob?.cancel()
        currentJob = null
        _progress.value = null
    }

    data class Candidate(
        val uri: Uri,
        val displayName: String,
        val size: Long,
        val lastModified: Long?,
        val type: MemoryType,
        val contentCreatedAt: Long?,
    )

    /**
     * Imports an entire directory tree picked via ACTION_OPEN_DOCUMENT_TREE.
     * Takes persistable read grant and releases it in a finally block at completion.
     */
    fun importFolder(treeUri: Uri): Job = launchImport {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val contentResolver = context.contentResolver
        var grantTaken = false

        try {
            try {
                contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                grantTaken = true
            } catch (_: SecurityException) {
                // Provider may not support persistable grants; proceed with active grant
            }

            _progress.value = ImportProgress.Scanning("Scanning folder...")
            val folderName = resolveTreeDisplayName(treeUri)
            val defaultSourceLabel = FileMetadata.resolveFolderLabel(folderName)

            val candidates = scanTree(treeUri)
            if (candidates.isEmpty()) {
                _progress.value = ImportProgress.Done(0, 0, 0)
                return@launchImport
            }

            // Oldest content first
            val sorted = candidates.sortedBy { it.contentCreatedAt ?: it.lastModified ?: Long.MAX_VALUE }
            processCandidates(sorted, defaultSourceLabel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _progress.value = ImportProgress.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            if (grantTaken) {
                try {
                    contentResolver.releasePersistableUriPermission(treeUri, takeFlags)
                } catch (_: Exception) {
                    // Ignore release errors to prevent grant leakage
                }
            }
        }
    }

    /**
     * Imports images picked via the Android photo picker.
     */
    fun importVisualMedia(uris: List<Uri>): Job = launchImport {
        if (uris.isEmpty()) return@launchImport
        _progress.value = ImportProgress.Scanning("Reading selected photos...")

        try {
            val candidates = uris.mapIndexed { index, uri ->
                queryFileMetadata(uri, defaultPrefix = "photo_$index", defaultType = MemoryType.IMAGE)
            }
            val sorted = candidates.sortedBy { it.contentCreatedAt ?: it.lastModified ?: Long.MAX_VALUE }
            processCandidates(sorted, defaultSourceLabel = "Photos")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _progress.value = ImportProgress.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Imports PDFs picked via the document file picker.
     */
    fun importPdfs(uris: List<Uri>): Job = launchImport {
        if (uris.isEmpty()) return@launchImport
        _progress.value = ImportProgress.Scanning("Reading selected PDFs...")

        try {
            val candidates = uris.mapIndexed { index, uri ->
                queryFileMetadata(uri, defaultPrefix = "document_$index", defaultType = MemoryType.PDF)
            }
            val sorted = candidates.sortedBy { it.contentCreatedAt ?: it.lastModified ?: Long.MAX_VALUE }
            processCandidates(sorted, defaultSourceLabel = "Files")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _progress.value = ImportProgress.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Imports bookmarks from a Netscape HTML bookmark export file.
     */
    fun importBookmarks(uri: Uri): Job = launchImport {
        _progress.value = ImportProgress.Scanning("Reading bookmarks...")
        try {
            val bookmarks = context.contentResolver.openInputStream(uri)?.use { stream ->
                BookmarkParser.parse(stream)
            } ?: run {
                _progress.value = ImportProgress.Done(0, 0, 0)
                return@launchImport
            }

            if (bookmarks.isEmpty()) {
                _progress.value = ImportProgress.Done(0, 0, 0)
                return@launchImport
            }

            val total = bookmarks.size
            var importedCount = 0
            var skippedCount = 0

            // Oldest bookmarks first by addDateMillis if available
            val sorted = bookmarks.sortedBy { it.addDateMillis ?: Long.MAX_VALUE }

            // The pages already saved, however their addresses were written: a page
            // shared from Chrome and also bookmarked is one memory, not two.
            val saved = importDao.linkTexts().flatMapTo(mutableSetOf()) { UrlKey.allIn(it) }

            for ((index, bookmark) in sorted.withIndex()) {
                coroutineContext.ensureActive()
                _progress.value = ImportProgress.Running(
                    current = index + 1,
                    total = total,
                    imported = importedCount,
                    skipped = skippedCount,
                    currentItem = bookmark.title ?: bookmark.url,
                )

                val bookmarkKey = "bookmark:${bookmark.url.trim()}"
                if (importDao.exists(bookmarkKey)) {
                    skippedCount++
                    continue
                }

                val urlKey = UrlKey.of(bookmark.url)
                if (urlKey != null && urlKey in saved) {
                    importDao.record(ImportedSource(key = bookmarkKey, importedAt = System.currentTimeMillis()))
                    skippedCount++
                    continue
                }
                // Two spellings of one page in the same export come in once.
                urlKey?.let { saved += it }

                val now = System.currentTimeMillis()
                val memory = Memory(
                    id = UUID.randomUUID().toString(),
                    type = MemoryType.LINK,
                    hasLink = true,
                    capturedAt = now,
                    contentCreatedAt = bookmark.addDateMillis,
                    sourceAppLabel = "Bookmarks",
                    rawText = bookmark.url,
                    title = bookmark.title,
                    extractedText = null, // Link reader (R4) handles reading pages
                )

                memoryDao.upsert(memory)
                importDao.record(ImportedSource(key = bookmarkKey, importedAt = now))
                importedCount++
            }

            _progress.value = ImportProgress.Done(total, importedCount, skippedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _progress.value = ImportProgress.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun processCandidates(
        candidates: List<Candidate>,
        defaultSourceLabel: String,
    ) {
        val total = candidates.size
        var importedCount = 0
        var skippedCount = 0
        val hasImages = candidates.any { it.type == MemoryType.IMAGE }

        if (hasImages) {
            readingOriginals.value = true
        }

        try {
            // Index existing originals by file size to accelerate SHA-256 duplicate checking
            val existingFiles = originals.files()
            val existingBySize = existingFiles.groupBy { it.length() }
            val existingHashCache = mutableMapOf<File, String>()

            for ((index, candidate) in candidates.withIndex()) {
                coroutineContext.ensureActive()
                _progress.value = ImportProgress.Running(
                    current = index + 1,
                    total = total,
                    imported = importedCount,
                    skipped = skippedCount,
                    currentItem = candidate.displayName,
                )

                // Level 1: Deduplication by filename + size before copying
                val nameSizeKey = "file:${candidate.displayName}:${candidate.size}"
                if (candidate.size > 0 && importDao.exists(nameSizeKey)) {
                    skippedCount++
                    continue
                }

                // Level 2: Stream content to a staging file while calculating SHA-256 in one pass
                val stagingId = UUID.randomUUID().toString()
                // The display name comes from another app's provider and could hold
                // path segments, so only a short run of letters and digits counts.
                val extension = candidate.displayName.substringAfterLast('.', "")
                    .takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
                    ?.lowercase(Locale.ROOT)
                    ?: if (candidate.type == MemoryType.PDF) "pdf" else "jpg"
                val stagingFile = File(context.cacheDir, "import_stage_$stagingId.$extension")

                val sha256 = try {
                    val input = context.contentResolver.openInputStream(candidate.uri)
                    if (input == null) {
                        skippedCount++
                        continue
                    }
                    input.use { src ->
                        stagingFile.outputStream().use { dst ->
                            Sha256.copyAndDigest(src, dst)
                        }
                    }
                } catch (_: Exception) {
                    stagingFile.delete()
                    skippedCount++
                    continue
                }

                val sha256Key = "sha256:$sha256"

                // Check if SHA-256 was already imported (including imported and subsequently deleted)
                if (importDao.exists(sha256Key)) {
                    stagingFile.delete()
                    if (candidate.size > 0) {
                        importDao.record(ImportedSource(nameSizeKey, System.currentTimeMillis()))
                    }
                    skippedCount++
                    continue
                }

                // Check SHA-256 against all currently stored originals
                val sameSizeFiles = existingBySize[stagingFile.length()].orEmpty()
                val isDuplicateOfOriginal = sameSizeFiles.any { file ->
                    val hash = existingHashCache.getOrPut(file) { Sha256.digest(file) }
                    hash == sha256
                }

                if (isDuplicateOfOriginal) {
                    stagingFile.delete()
                    val now = System.currentTimeMillis()
                    val sourcesToRecord = mutableListOf(ImportedSource(sha256Key, now))
                    if (candidate.size > 0) {
                        sourcesToRecord.add(ImportedSource(nameSizeKey, now))
                    }
                    importDao.recordAll(sourcesToRecord)
                    skippedCount++
                    continue
                }

                // Not a duplicate: store original and create Memory row
                val memoryId = UUID.randomUUID().toString()
                val storedFile = try {
                    originals.moveIn(stagingFile, memoryId, extension)
                } catch (_: Exception) {
                    stagingFile.delete()
                    skippedCount++
                    continue
                }

                // EXIF, else the name, else the provider. The candidate's date is
                // only the name's or the provider's: EXIF can be read once the file
                // is ours, and it must come first, since a copied or renamed photo
                // keeps its EXIF but not its name or modified time.
                val finalCreatedAt = if (candidate.type == MemoryType.IMAGE) {
                    FileMetadata.readExifDate(storedFile) ?: candidate.contentCreatedAt
                } else {
                    candidate.contentCreatedAt
                }

                val now = System.currentTimeMillis()
                val memory = Memory(
                    id = memoryId,
                    type = candidate.type,
                    capturedAt = now,
                    contentCreatedAt = finalCreatedAt,
                    sourceAppLabel = defaultSourceLabel,
                    localUri = originals.uriFor(storedFile),
                    title = if (candidate.type == MemoryType.PDF) candidate.displayName.removeSuffix(".pdf") else null,
                    extractedText = null, // Unread; OCR / reading queues process it
                )

                memoryDao.upsert(memory)

                val sourcesToRecord = mutableListOf(ImportedSource(sha256Key, now))
                if (candidate.size > 0) {
                    sourcesToRecord.add(ImportedSource(nameSizeKey, now))
                }
                importDao.recordAll(sourcesToRecord)

                // Track newly added file in hash cache for subsequent candidates in this batch
                existingHashCache[storedFile] = sha256
                importedCount++
            }

            _progress.value = ImportProgress.Done(total, importedCount, skippedCount)
        } finally {
            withContext(NonCancellable) {
                // Prevent readingOriginals from remaining stuck true if no unread images remain
                if (hasImages && readingDao.countUnreadImages() == 0) {
                    readingOriginals.value = false
                }
            }
        }
    }

    private fun scanTree(treeUri: Uri): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        val contentResolver = context.contentResolver
        val queue = ArrayDeque<String>()

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        queue.add(rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        while (!queue.isEmpty()) {
            val parentDocId = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: "unnamed"
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val size = if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val lastModified = if (modIdx != -1 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else null

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(docId)
                    } else {
                        val memoryType = when {
                            mime.startsWith("image/") || isImageExtension(name) -> MemoryType.IMAGE
                            mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> MemoryType.PDF
                            else -> null
                        }

                        if (memoryType != null) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val contentCreatedAt = FileMetadata.resolveContentCreatedAt(
                                exifDate = null, // Defer stream EXIF reading to avoid early double-read
                                filename = name,
                                providerLastModified = lastModified,
                            )
                            candidates.add(
                                Candidate(
                                    uri = docUri,
                                    displayName = name,
                                    size = size,
                                    lastModified = lastModified,
                                    type = memoryType,
                                    contentCreatedAt = contentCreatedAt,
                                )
                            )
                        }
                    }
                }
            }
        }

        return candidates
    }

    private fun queryFileMetadata(uri: Uri, defaultPrefix: String, defaultType: MemoryType): Candidate {
        var name: String? = null
        var size = 0L
        var lastModified: Long? = null

        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
            // Use defaults if provider query fails
        }

        // A plain file has no provider to ask, and some providers leave out the
        // size: name it by its path and size it by its descriptor, so the
        // name-and-size check still applies before anything is copied.
        if (name == null && uri.scheme == "file") name = uri.lastPathSegment
        if (size <= 0L) {
            size = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            } catch (_: Exception) {
                0L
            }.coerceAtLeast(0L)
        }

        val ext = if (defaultType == MemoryType.PDF) "pdf" else "jpg"
        val displayName = name ?: "$defaultPrefix.$ext"
        val contentCreatedAt = FileMetadata.resolveContentCreatedAt(
            exifDate = null,
            filename = displayName,
            providerLastModified = lastModified,
        )

        return Candidate(
            uri = uri,
            displayName = displayName,
            size = size,
            lastModified = lastModified,
            type = defaultType,
            contentCreatedAt = contentCreatedAt,
        )
    }

    private fun resolveTreeDisplayName(treeUri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        // docId often looks like "primary:Pictures/Screenshots" or "raw:/storage/emulated/0/DCIM"
        val clean = docId.substringAfterLast(':').substringAfterLast('/')
        return clean.ifBlank { "Folder" }
    }

    private fun isImageExtension(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".heic")
    }
}
