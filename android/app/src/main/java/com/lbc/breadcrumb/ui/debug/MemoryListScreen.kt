package com.lbc.breadcrumb.ui.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.imports.ImportProgress
import com.lbc.breadcrumb.sync.UploadWorker
import com.lbc.breadcrumb.ui.theme.Mono
import com.lbc.breadcrumb.ui.theme.Sans
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every memory on the phone, newest first, with the state each workstream
 * moves it through: its chips, whether its text has been read, whether it
 * has synced. A developer's view, not a product screen: the search screens
 * replace it as the app's face, and it stays reachable in debug builds.
 *
 * Each workstream may add a line or an action here for what it needs to see
 * (an import button, a sync button), kept small.
 */
@Composable
fun MemoryListScreen() {
    val app = LocalContext.current.applicationContext as BreadcrumbApp
    val memoryDao = remember { app.database.memoryDao() }
    val readingDao = remember { app.database.readingDao() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val memories by remember(searchQuery) {
        if (searchQuery.isBlank()) {
            memoryDao.observeAll()
        } else {
            readingDao.observeSearch(searchQuery)
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var nextSample by rememberSaveable { mutableIntStateOf(0) }
    val importProgress by app.importer.progress.collectAsStateWithLifecycle()

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { app.importer.importFolder(it) }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) app.importer.importVisualMedia(uris)
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) app.importer.importPdfs(uris)
    }

    val bookmarkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { app.importer.importBookmarks(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.debug_title),
                style = chrome,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text("${memories.size}", style = chrome, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                app.applicationScope.launch {
                    memoryDao.clearEverywhere(System.currentTimeMillis())
                    app.originals.clear()
                    // Or importing again after a Clear would skip everything as seen.
                    app.database.importDao().clear()
                    UploadWorker.schedule(app)
                }
            }) { Text(stringResource(R.string.debug_clear), style = chrome) }
            TextButton(onClick = {
                val sample = SAMPLES[nextSample % SAMPLES.size](app)
                nextSample += 1
                app.applicationScope.launch { memoryDao.upsert(sample) }
            }) { Text("+", style = chrome.copy(fontSize = 18.sp)) }
        }
        ServerStatusLine(app.server)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = {
                Text(
                    "Search words (local FTS)...",
                    style = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            textStyle = body.copy(color = MaterialTheme.colorScheme.onBackground),
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("×", style = chrome.copy(fontSize = 18.sp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("import:", style = chrome, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { folderLauncher.launch(null) }) {
                Text("folder", style = chrome)
            }
            TextButton(onClick = {
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text("photos", style = chrome)
            }
            TextButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                Text("pdfs", style = chrome)
            }
            TextButton(onClick = { bookmarkLauncher.launch(arrayOf("text/html", "*/*")) }) {
                Text("bookmarks", style = chrome)
            }
        }

        var geminiReadsMemories by remember { mutableStateOf(app.syncSettings.geminiReadsMemories) }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Gemini reads my memories", style = chrome, color = MaterialTheme.colorScheme.onBackground)
            androidx.compose.material3.Switch(
                checked = geminiReadsMemories,
                onCheckedChange = { checked ->
                    geminiReadsMemories = checked
                    app.syncSettings.geminiReadsMemories = checked
                    if (!checked) {
                        app.applicationScope.launch {
                            app.database.uploadDao().unsendAll()
                            app.server.forgetAll()
                        }
                    } else {
                        UploadWorker.runNow(app)
                    }
                },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.debug_upload), style = chrome, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Out of any backoff: after starting the server, or running adb reverse.
            TextButton(onClick = { UploadWorker.runNow(app) }) {
                Text(stringResource(R.string.debug_sync_now), style = chrome)
            }
            // Refused memories are not retried on their own: the same bytes would be refused again.
            TextButton(onClick = {
                app.applicationScope.launch {
                    if (app.database.uploadDao().retryFailed() > 0) UploadWorker.runNow(app)
                }
            }) {
                Text(stringResource(R.string.debug_retry_refused), style = chrome)
            }
        }

        val progressText = when (val p = importProgress) {
            is ImportProgress.Scanning -> p.message
            is ImportProgress.Running -> "importing ${p.current}/${p.total} · ${p.imported} added · ${p.skipped} skipped"
            is ImportProgress.Done -> "import done · ${p.imported} added · ${p.skipped} skipped"
            is ImportProgress.Failed -> "import failed · ${p.reason}"
            null -> null
        }
        if (progressText != null) {
            Text(
                progressText,
                style = chrome,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (memories.isEmpty()) {
            Text(
                if (searchQuery.isNotBlank()) "No memories matching \"$searchQuery\"" else stringResource(R.string.debug_empty),
                style = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(memories, key = { it.id }) { memory ->
                    MemoryRow(memory, onDelete = {
                        app.applicationScope.launch {
                            memoryDao.deleteEverywhere(memory, System.currentTimeMillis())
                            app.originals.delete(memory)
                            UploadWorker.schedule(app)
                        }
                    })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(memory: Memory, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(
                    memory.chips.joinToString(" + ") { it.name.lowercase() },
                    readingState(memory),
                    memory.syncState.name.lowercase(),
                    memory.sourceAppLabel?.let { "from $it" },
                ).joinToString(" · "),
                style = chrome,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                headline(memory),
                style = body,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded && !memory.extractedText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "read: ${memory.extractedText}",
                    style = chrome,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            memory.note?.let {
                Text("note: $it", style = body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            memory.summary?.let {
                Text("summary: $it", style = body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text(
                TIME.format(Date(memory.capturedAt)),
                style = chrome,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.debug_delete), style = chrome) }
    }
}

/** What the phone has read out of it, for the kinds it reads. */
private fun readingState(memory: Memory): String? {
    val text = memory.extractedText
    return when {
        text == null -> if (memory.type == MemoryType.IMAGE || memory.type == MemoryType.PDF) "not read" else null
        text.isEmpty() -> "no text found"
        else -> {
            val lines = text.lineSequence().filter { it.isNotBlank() }.count()
            "read · $lines lines · ${text.length} chars"
        }
    }
}

private fun headline(memory: Memory): String =
    memory.title?.takeIf { it.isNotBlank() }
        ?: memory.rawText?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: memory.extractedText?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: "(nothing to show yet)"

internal val chrome = TextStyle(fontFamily = Mono, fontSize = 12.sp)
private val body = TextStyle(fontFamily = Sans, fontSize = 15.sp)
private val TIME = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * Sample memories for trying the list, and later the search screens, before
 * any capture surface exists. Built fresh each time, so each gets its own id
 * and timestamp.
 */
private val SAMPLES: List<(BreadcrumbApp) -> Memory> = listOf(
    { _ ->
        Memory(
            type = MemoryType.TEXT,
            rawText = "Try the brewery burger at that place in Indiranagar, or grab espresso at Café Coffee Day, Priya said it's the best in town",
        )
    },
    { _ ->
        Memory(
            type = MemoryType.LINK,
            hasLink = true,
            title = "Software Engineering Intern, Qualcomm",
            rawText = "https://www.linkedin.com/jobs/view/4120938817",
        )
    },
    { _ ->
        Memory(
            type = MemoryType.TEXT,
            rawText = "Arjun from the hackathon, works on modem firmware at Qualcomm, +91 98450 12345",
            note = "met at the demo table",
        )
    },
    { app ->
        createSampleImageMemory(app, withText = true)
    },
    { app ->
        createSampleImageMemory(app, withText = false)
    },
    { app ->
        createSamplePdfMemory(app)
    },
)

private fun createSampleImageMemory(app: BreadcrumbApp, withText: Boolean): Memory {
    val id = UUID.randomUUID().toString()
    val width = 600
    val height = 300
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }

    if (withText) {
        canvas.drawColor(android.graphics.Color.WHITE)
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 28f
        canvas.drawText("Software Engineering Intern, Qualcomm", 20f, 60f, paint)
        canvas.drawText("Apply by April 30. Bengaluru.", 20f, 110f, paint)
        canvas.drawText("Priya says apply early via career portal", 20f, 160f, paint)
        canvas.drawText("Requirements: Kotlin, C++, algorithms", 20f, 210f, paint)
    } else {
        canvas.drawColor(android.graphics.Color.DKGRAY)
    }

    val dir = File(app.filesDir, "originals").apply { mkdirs() }
    val file = File(dir, "$id.jpg")
    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    bitmap.recycle()

    return Memory(
        id = id,
        type = MemoryType.IMAGE,
        localUri = app.originals.uriFor(file),
        sourceAppLabel = if (withText) "Screenshots" else "Camera",
    )
}

private fun createSamplePdfMemory(app: BreadcrumbApp): Memory {
    val id = UUID.randomUUID().toString()
    val doc = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(600, 800, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 24f
        isAntiAlias = true
    }
    canvas.drawText("Qualcomm Internship Guidelines", 50f, 80f, paint)
    canvas.drawText("Modem Architecture & Firmware Team", 50f, 130f, paint)
    canvas.drawText("Bengaluru Office, Bagmane Tech Park", 50f, 180f, paint)
    canvas.drawText("Requirements: C++, Kotlin, algorithms", 50f, 230f, paint)

    doc.finishPage(page)
    val dir = File(app.filesDir, "originals").apply { mkdirs() }
    val file = File(dir, "$id.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()

    return Memory(
        id = id,
        type = MemoryType.PDF,
        title = "Qualcomm Internship Guidelines",
        localUri = app.originals.uriFor(file),
        sourceAppLabel = "Documents",
    )
}
