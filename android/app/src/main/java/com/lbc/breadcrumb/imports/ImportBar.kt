package com.lbc.breadcrumb.imports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.ui.common.CrumbTrail
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder
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
    val line = importLine(progress)
    var lastLine by remember { mutableStateOf<String?>(null) }
    if (line != null) {
        lastLine = line
    }

    // Auto-dismiss 6 seconds after import finishes or fails
    LaunchedEffect(progress) {
        if (progress is ImportProgress.Done || progress is ImportProgress.Failed) {
            delay(6000L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = line != null,
        enter = fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(220)) { it / 2 },
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(240)) { it / 2 },
        modifier = modifier,
    ) {
        val isWalking = progress is ImportProgress.Scanning || progress is ImportProgress.Running
        Box(
            modifier = Modifier
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(InkElevated)
                .border(1.dp, InkFieldBorder, RoundedCornerShape(23.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CrumbTrail(
                    modifier = Modifier.size(15.dp),
                    walking = isWalking,
                )
                Text(
                    text = line ?: lastLine ?: "",
                    style = monoStyle(12.sp, BoneDim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
