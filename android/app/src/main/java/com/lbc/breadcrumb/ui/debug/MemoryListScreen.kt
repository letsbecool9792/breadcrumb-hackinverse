package com.lbc.breadcrumb.ui.debug

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
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
    val dao = remember { app.database.memoryDao() }
    val memories by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var nextSample by rememberSaveable { mutableIntStateOf(0) }

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
                    dao.clearEverywhere(System.currentTimeMillis())
                    app.originals.clear()
                }
            }) { Text(stringResource(R.string.debug_clear), style = chrome) }
            TextButton(onClick = {
                val sample = SAMPLES[nextSample % SAMPLES.size]()
                nextSample += 1
                app.applicationScope.launch { dao.upsert(sample) }
            }) { Text("+", style = chrome.copy(fontSize = 18.sp)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (memories.isEmpty()) {
            Text(
                stringResource(R.string.debug_empty),
                style = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(memories, key = { it.id }) { memory ->
                    MemoryRow(memory, onDelete = {
                        app.applicationScope.launch {
                            dao.deleteEverywhere(memory, System.currentTimeMillis())
                            app.originals.delete(memory)
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
    Row(
        Modifier
            .fillMaxWidth()
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
        else -> "read · ${text.length} chars"
    }
}

private fun headline(memory: Memory): String =
    memory.title?.takeIf { it.isNotBlank() }
        ?: memory.rawText?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: memory.extractedText?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: "(nothing to show yet)"

private val chrome = TextStyle(fontFamily = Mono, fontSize = 12.sp)
private val body = TextStyle(fontFamily = Sans, fontSize = 15.sp)
private val TIME = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * Sample memories for trying the list, and later the search screens, before
 * any capture surface exists. Built fresh each time, so each gets its own id
 * and timestamp.
 */
private val SAMPLES: List<() -> Memory> = listOf(
    {
        Memory(
            type = MemoryType.TEXT,
            rawText = "Try the brewery burger at that place in Indiranagar, Priya said it's the best in town",
        )
    },
    {
        Memory(
            type = MemoryType.LINK,
            hasLink = true,
            title = "Software Engineering Intern, Qualcomm",
            rawText = "https://www.linkedin.com/jobs/view/4120938817",
        )
    },
    {
        Memory(
            type = MemoryType.TEXT,
            rawText = "Arjun from the hackathon, works on modem firmware at Qualcomm, +91 98450 12345",
            note = "met at the demo table",
        )
    },
)
