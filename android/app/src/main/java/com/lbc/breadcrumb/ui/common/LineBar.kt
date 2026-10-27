package com.lbc.breadcrumb.ui.common

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
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder

/**
 * A line over the search field, in the undo bar's pill (design §12, §14): the
 * import's, the model's, a backup's. A CrumbTrail 15dp, walking while [walking],
 * then [line] in mono 12sp BoneDim, one line. Shown while there is a line; the
 * last stays drawn while it slides away. Enter fadeIn(160) + slideInVertically(220)
 * { it / 2 }, exit fadeOut(200) + slideOutVertically(240) { it / 2 }.
 */
@Composable
fun LineBar(
    line: String?,
    walking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastLine by remember { mutableStateOf<String?>(null) }
    if (line != null) {
        lastLine = line
    }

    AnimatedVisibility(
        visible = line != null,
        enter = fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(220)) { it / 2 },
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(240)) { it / 2 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(InkElevated)
                .border(1.dp, InkFieldBorder, RoundedCornerShape(23.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CrumbTrail(
                    modifier = Modifier.size(15.dp),
                    walking = walking,
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
