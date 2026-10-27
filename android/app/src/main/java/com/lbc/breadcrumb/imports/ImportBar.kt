package com.lbc.breadcrumb.imports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lbc.breadcrumb.ui.common.LineBar
import kotlinx.coroutines.delay

/**
 * The import bar over the search field, above the undo bar, while there is a line (docs/design.md §14).
 *
 * 46dp pill, padding 18 horizontal, spacing 10:
 * - CrumbTrail 15dp, walking while the import runs.
 * - The line in mono 12sp BoneDim, one line.
 * - A tap dismisses it ([onDismiss]); it goes by itself 6 s after the import finishes or fails.
 * - Enter: fadeIn(160) + slideInVertically(220) { it / 2 }
 * - Exit: fadeOut(200) + slideOutVertically(240) { it / 2 }, the last line stays drawn while sliding away.
 */
@Composable
fun ImportBar(
    progress: ImportProgress?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-dismiss 6 seconds after import finishes or fails
    LaunchedEffect(progress) {
        if (progress is ImportProgress.Done || progress is ImportProgress.Failed) {
            delay(6000L)
            onDismiss()
        }
    }

    LineBar(
        line = importLine(progress),
        walking = progress is ImportProgress.Scanning || progress is ImportProgress.Running,
        onClick = onDismiss,
        modifier = modifier,
    )
}

/** Convenience overload taking [BulkImporter] directly. */
@Composable
fun ImportBar(
    importer: BulkImporter,
    modifier: Modifier = Modifier,
) {
    val progress by importer.progress.collectAsState()
    ImportBar(
        progress = progress,
        onDismiss = { importer.forget() },
        modifier = modifier,
    )
}
