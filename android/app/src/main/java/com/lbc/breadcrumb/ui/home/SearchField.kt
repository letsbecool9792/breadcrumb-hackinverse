package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.common.ClearGlyph
import com.lbc.breadcrumb.ui.common.CrumbTrail
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberContainerDark
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder
import com.lbc.breadcrumb.ui.theme.InkOutline

/**
 * The one search field (design §8). Results are live as it is typed, so the
 * search key only puts the keyboard away. The crumbs walk while the ranked
 * answer is on its way ([ranking]), and an amber light sits behind the pill,
 * stronger while it is in use and breathing while a search is out.
 *
 * [searching] is whether a search is on screen at all.
 */
@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    ranking: Boolean,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    // The light behind the pill. Read while drawing, so breathing never recomposes.
    val base = animateFloatAsState(
        targetValue = if (focused || searching) 0.13f else 0.06f,
        animationSpec = tween(400),
        label = "glowBase",
    )
    val breath = if (searching) {
        rememberInfiniteTransition(label = "glowBreath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        null
    }

    val shape = RoundedCornerShape(27.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .drawBehind {
                val glow = base.value + 0.07f * (breath?.value ?: 0f)
                val radius = size.width * 0.62f
                scale(scaleX = 1f, scaleY = 0.42f, pivot = center) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AmberBright.copy(alpha = glow), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            .clip(shape)
            .background(InkElevated)
            .border(1.dp, if (focused) AmberContainerDark else InkFieldBorder, shape)
            .padding(start = 20.dp, end = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CrumbTrail(Modifier.size(17.dp), walking = ranking)
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChange(it.isFocused)
                        },
                    textStyle = monoStyle(13.sp, Bone),
                    singleLine = true,
                    cursorBrush = SolidColor(AmberBright),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
                if (query.isEmpty()) {
                    Text(stringResource(R.string.search_hint), style = monoStyle(13.sp, InkOutline))
                }
            }
            if (query.isNotEmpty()) {
                val description = stringResource(R.string.search_clear)
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(InkFieldBorder)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onQueryChange("") }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    ClearGlyph(Modifier.size(9.dp), BoneDim)
                }
            }
        }
    }
}
