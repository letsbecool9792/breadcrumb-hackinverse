package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lbc.breadcrumb.ui.keep.KeepSomethingButton

/**
 * The search field with the "+" 10dp to its right (design §4, §8). The "+" steps
 * aside while the field is in use, focused or holding a query, and the field
 * takes its room.
 */
@Composable
internal fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    ranking: Boolean,
    hasDraft: Boolean,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            searching = searching,
            ranking = ranking,
            modifier = Modifier.weight(1f),
            onFocusChange = { focused = it },
        )
        AnimatedVisibility(
            visible = !focused && query.isEmpty(),
            enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(200)),
            label = "plus",
        ) {
            KeepSomethingButton(hasDraft = hasDraft, onClick = onPlus, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
