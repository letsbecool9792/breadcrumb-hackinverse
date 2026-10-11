package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.InkOutline
import com.lbc.breadcrumb.ui.theme.Mono
import com.lbc.breadcrumb.ui.theme.Sans
import com.lbc.breadcrumb.ui.theme.Serif

/**
 * Shared typography helpers for the Breadcrumb screens (docs/design.md §1).
 *
 * Every text style names its face, through these helpers. A Text given its own
 * style takes nothing from the theme.
 */

/** Young Serif: sturdy old-style serif in one regular weight. */
fun serif(
    size: TextUnit,
    color: Color = Bone,
    lineHeight: TextUnit = size * 1.15f,
): TextStyle = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    color = color,
    lineHeight = lineHeight,
)

/** Instrument Sans: variable face serving every weight used on the page. */
fun sans(
    size: TextUnit,
    color: Color = Bone,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = weight,
    fontSize = size,
    color = color,
    lineHeight = lineHeight,
)

/** IBM Plex Mono, for chrome. */
fun monoStyle(
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = Mono,
    fontWeight = weight,
    fontSize = size,
    color = color,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

/** Chrome metadata: mono 10sp InkOutline, letterSpacing 0.3sp ("2d · chrome"). */
val metaStyle: TextStyle = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    color = InkOutline,
    letterSpacing = 0.3.sp,
)

/**
 * Quiet empty-state message (design §1):
 * Column, padding 26dp horizontal, 40dp vertical, spacing 9dp:
 * title serif 22sp; hint mono 11sp InkOutline, lineHeight 18sp.
 */
@Composable
fun Quiet(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 26.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = title,
            style = serif(22.sp),
        )
        Text(
            text = hint,
            style = monoStyle(11.sp, InkOutline, lineHeight = 18.sp),
        )
    }
}
