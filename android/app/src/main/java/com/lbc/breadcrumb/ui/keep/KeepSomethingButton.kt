package com.lbc.breadcrumb.ui.keep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.common.PlusGlyph
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberOnContainer
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.Ink

/**
 * The "+" button beside the search field (docs/design.md §8).
 *
 * A 54dp circle in [AmberBright], clickable, holding an 18dp plus in [AmberOnContainer]
 * with 2.2dp strokes and round caps. Content description "Keep something", or
 * "Keep something — a draft is waiting" when there is one, and then a dot at the top end
 * (padding 3): a 12dp Ink circle with a Bone dot inset 2.5dp. It opens the "+" sheet.
 */
@Composable
fun KeepSomethingButton(
    hasDraft: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (hasDraft) {
        stringResource(R.string.keep_button_description_draft)
    } else {
        stringResource(R.string.keep_button_description)
    }

    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(AmberBright)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                this.contentDescription = description
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        PlusGlyph(
            modifier = Modifier.size(18.dp),
            color = AmberOnContainer,
            strokeWidth = 2.2.dp,
        )

        if (hasDraft) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(12.dp)
                    .background(Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Bone, CircleShape),
                )
            }
        }
    }
}
