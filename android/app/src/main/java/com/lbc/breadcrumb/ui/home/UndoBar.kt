package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder
import java.util.Locale

/**
 * The pill over the search field after a delete (design §12): "deleted · the
 * title", and Undo. [what] is the line to show, null when nothing was deleted;
 * the last line stays drawn while the bar slides away.
 */
@Composable
internal fun UndoBar(what: String?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val last = remember { LastLine() }
    if (what != null) last.value = what
    AnimatedVisibility(
        visible = what != null,
        modifier = modifier,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it / 2 },
    ) {
        val shape = RoundedCornerShape(23.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(InkElevated)
                .border(1.dp, InkFieldBorder, shape)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${stringResource(R.string.undo_deleted)} · ${last.value.orEmpty()}",
                style = monoStyle(12.sp, BoneDim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.capture_undo),
                style = sans(14.sp, AmberBright, FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** What the bar says for a memory just deleted: its title, lowercase. */
internal fun undoLine(title: String): String = title.lowercase(Locale.ROOT)

private class LastLine {
    var value: String? = null
}
