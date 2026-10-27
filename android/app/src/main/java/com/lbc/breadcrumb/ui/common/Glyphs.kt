package com.lbc.breadcrumb.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.CrumbBright
import com.lbc.breadcrumb.ui.theme.CrumbDim
import com.lbc.breadcrumb.ui.theme.CrumbFaint
import com.lbc.breadcrumb.ui.theme.CrumbMid
import com.lbc.breadcrumb.ui.theme.InkBorder
import com.lbc.breadcrumb.ui.theme.InkGrabber
import com.lbc.breadcrumb.ui.theme.InkOutline
import kotlin.math.abs

/**
 * Common marks, glyphs, and sheet grabber (docs/design.md §2 and §11).
 */

private data class CrumbPoint(
    val x: Float,
    val y: Float,
    val r: Float,
    val color: Color,
)

private val CRUMB_POINTS = listOf(
    CrumbPoint(4f, 15.5f, 1.6f, CrumbFaint),
    CrumbPoint(8.4f, 12.4f, 2.1f, CrumbDim),
    CrumbPoint(13.2f, 8.6f, 2.7f, CrumbMid),
    CrumbPoint(17f, 4.2f, 3f, CrumbBright),
)

/**
 * Light intensity for crumb [i] at lap [phase] in [0, 1].
 *
 * step = 1/5, peak = step·i + step/2,
 * lit = clamp(1 − |phase − peak| / step, 0, 1).
 * Each crumb peaks in turn; the fifth step (phase >= 0.8 to 1.0) is rest.
 */
internal fun stepLight(phase: Float, i: Int): Float {
    val step = 0.2f
    val peak = step * i + step / 2f
    return (1f - abs(phase - peak) / step).coerceIn(0f, 1f)
}

/**
 * The logo's four crumbs drawn on a 20-unit square (design §2).
 * Still: all crumbs at full alpha.
 * Walking: infinite linear lap of 1,300 ms; read while drawing, loop only exists while walking.
 */
@Composable
fun CrumbTrail(
    modifier: Modifier = Modifier,
    walking: Boolean = false,
) {
    val phaseState = if (walking) {
        val transition = rememberInfiniteTransition(label = "CrumbTrailWalking")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1300, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "CrumbTrailPhase",
        )
    } else {
        null
    }

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val u = minOf(size.width, size.height) / 20f
        val currentPhase = phaseState?.value ?: 0f

        for (i in CRUMB_POINTS.indices) {
            val crumb = CRUMB_POINTS[i]
            val lit = if (walking) stepLight(currentPhase, i) else 0f
            val alpha = if (walking) 0.28f + 0.72f * lit else 1f
            val scale = if (walking) 0.82f + 0.3f * lit else 1f

            drawCircle(
                color = crumb.color.copy(alpha = alpha),
                radius = crumb.r * u * scale,
                center = Offset(crumb.x * u, crumb.y * u),
            )
        }
    }
}

/**
 * Document glyph (design §2):
 * A page in outlineVariant (InkBorder), corner radius 3dp;
 * a folded corner at top right, square of 28% of width in InkOutline at alpha 0.55 × 0.35;
 * five ruled lines 2dp high, rounded, from x = 18% width, at y = height × (0.36 + 0.12·i),
 * widths 44%, 64%, 58%, 64%, 36% of width, in InkOutline at alpha 0.55.
 */
@Composable
fun DocumentGlyph(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cornerRadiusPx = 3.dp.toPx()
        val pagePath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset.Zero, size),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
            )
        }
        drawPath(path = pagePath, color = InkBorder)

        // Folded corner at top right: square of 28% of width clipped to page top-right corner
        val foldSize = size.width * 0.28f
        clipPath(pagePath) {
            drawRect(
                color = InkOutline.copy(alpha = 0.55f * 0.35f),
                topLeft = Offset(size.width - foldSize, 0f),
                size = Size(foldSize, foldSize),
            )
        }

        // Five ruled lines 2dp high, rounded, from x = 18% width
        val lineFractions = floatArrayOf(0.44f, 0.64f, 0.58f, 0.64f, 0.36f)
        val lineHeightPx = 2.dp.toPx()
        val lineRadiusPx = lineHeightPx / 2f
        val lineX = size.width * 0.18f
        val lineColor = InkOutline.copy(alpha = 0.55f)

        for (i in 0 until 5) {
            val y = size.height * (0.36f + 0.12f * i)
            val lineWidth = size.width * lineFractions[i]
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(lineX, y),
                size = Size(lineWidth, lineHeightPx),
                cornerRadius = CornerRadius(lineRadiusPx, lineRadiusPx),
            )
        }
    }
}

/**
 * Note glyph (design §2):
 * Four rounded lines 3dp high with 4dp gaps, widths 70% (InkOutline α0.55),
 * 100%, 88%, 94% (InkBorder).
 */
@Composable
fun NoteGlyph(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val lineH = 3.dp.toPx()
        val gap = 4.dp.toPx()
        val radiusPx = lineH / 2f
        val totalH = 4 * lineH + 3 * gap
        val startY = if (size.height > totalH) (size.height - totalH) / 2f else 0f

        val widths = floatArrayOf(0.70f, 1.00f, 0.88f, 0.94f)
        val colors = arrayOf(
            InkOutline.copy(alpha = 0.55f),
            InkBorder,
            InkBorder,
            InkBorder,
        )

        for (i in 0 until 4) {
            val y = startY + i * (lineH + gap)
            val w = size.width * widths[i]
            drawRoundRect(
                color = colors[i],
                topLeft = Offset(0f, y),
                size = Size(w, lineH),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
        }
    }
}

/**
 * Picture glyph (design §2): 15dp, stroke 1.4u on 16-unit grid.
 * Rounded rect at (1.5, 2.5) size 13×11, radius 2;
 * polyline (2,12) (6.5,7.5) (10,11) (11.5,9.5) (14,12);
 * sun: filled circle r 1.2 at (11, 6).
 */
@Composable
fun PictureGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
) {
    Canvas(modifier = modifier) {
        val u = minOf(size.width, size.height) / 16f
        if (u <= 0f) return@Canvas
        val stroke = Stroke(width = 1.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawRoundRect(
            color = color,
            topLeft = Offset(1.5f * u, 2.5f * u),
            size = Size(13f * u, 11f * u),
            cornerRadius = CornerRadius(2f * u, 2f * u),
            style = stroke,
        )

        val polyline = Path().apply {
            moveTo(2f * u, 12f * u)
            lineTo(6.5f * u, 7.5f * u)
            lineTo(10f * u, 11f * u)
            lineTo(11.5f * u, 9.5f * u)
            lineTo(14f * u, 12f * u)
        }
        drawPath(path = polyline, color = color, style = stroke)

        drawCircle(
            color = color,
            radius = 1.2f * u,
            center = Offset(11f * u, 6f * u),
        )
    }
}

/**
 * Share glyph (design §2): 18dp on 16-unit grid.
 * Lines from (4,8) to (12,3.5) and to (12,12.5), stroke 1.4u;
 * filled circles r 2.1u at all three points.
 */
@Composable
fun ShareGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
) {
    Canvas(modifier = modifier) {
        val u = minOf(size.width, size.height) / 16f
        if (u <= 0f) return@Canvas
        val strokeWidthPx = 1.4f * u

        drawLine(
            color = color,
            start = Offset(4f * u, 8f * u),
            end = Offset(12f * u, 3.5f * u),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(4f * u, 8f * u),
            end = Offset(12f * u, 12.5f * u),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        val r = 2.1f * u
        drawCircle(color = color, radius = r, center = Offset(4f * u, 8f * u))
        drawCircle(color = color, radius = r, center = Offset(12f * u, 3.5f * u))
        drawCircle(color = color, radius = r, center = Offset(12f * u, 12.5f * u))
    }
}

/**
 * Bin glyph (design §2): 18dp, stroke 1.5u on 16-unit grid.
 * Lid line (2.5,4)–(13.5,4);
 * handle polyline (6,4) (6.5,2) (9.5,2) (10,4);
 * body polyline (3.8,4) (4.8,14) (11.2,14) (12.2,4).
 */
@Composable
fun BinGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
) {
    Canvas(modifier = modifier) {
        val u = minOf(size.width, size.height) / 16f
        if (u <= 0f) return@Canvas
        val stroke = Stroke(width = 1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawLine(
            color = color,
            start = Offset(2.5f * u, 4f * u),
            end = Offset(13.5f * u, 4f * u),
            strokeWidth = 1.5f * u,
            cap = StrokeCap.Round,
        )

        val handle = Path().apply {
            moveTo(6f * u, 4f * u)
            lineTo(6.5f * u, 2f * u)
            lineTo(9.5f * u, 2f * u)
            lineTo(10f * u, 4f * u)
        }
        drawPath(path = handle, color = color, style = stroke)

        val body = Path().apply {
            moveTo(3.8f * u, 4f * u)
            lineTo(4.8f * u, 14f * u)
            lineTo(11.2f * u, 14f * u)
            lineTo(12.2f * u, 4f * u)
        }
        drawPath(path = body, color = color, style = stroke)
    }
}

/**
 * Leaves the app glyph (design §2): 15dp, stroke 1.6u on 16-unit grid.
 * Box polyline (6,3) (3,3) (3,13) (13,13) (13,10);
 * arrow head (9.5,2.5) (13.5,2.5) (13.5,6.5);
 * shaft (13,3)–(7.5,8.5).
 */
@Composable
fun LeavesTheAppGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
) {
    Canvas(modifier = modifier) {
        val u = minOf(size.width, size.height) / 16f
        if (u <= 0f) return@Canvas
        val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val box = Path().apply {
            moveTo(6f * u, 3f * u)
            lineTo(3f * u, 3f * u)
            lineTo(3f * u, 13f * u)
            lineTo(13f * u, 13f * u)
            lineTo(13f * u, 10f * u)
        }
        drawPath(path = box, color = color, style = stroke)

        val arrowHead = Path().apply {
            moveTo(9.5f * u, 2.5f * u)
            lineTo(13.5f * u, 2.5f * u)
            lineTo(13.5f * u, 6.5f * u)
        }
        drawPath(path = arrowHead, color = color, style = stroke)

        drawLine(
            color = color,
            start = Offset(13f * u, 3f * u),
            end = Offset(7.5f * u, 8.5f * u),
            strokeWidth = 1.6f * u,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Clear × glyph (design §2):
 * Two diagonals across the whole box, stroke 1.5dp, round caps.
 */
@Composable
fun ClearGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
    strokeWidth: Dp = 1.5.dp,
) {
    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        // Across the whole box, the round caps kept inside it.
        val lo = strokePx / 2f
        val hiX = size.width - lo
        val hiY = size.height - lo
        if (hiX <= lo || hiY <= lo) return@Canvas

        drawLine(
            color = color,
            start = Offset(lo, lo),
            end = Offset(hiX, hiY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(hiX, lo),
            end = Offset(lo, hiY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Plus glyph (design §2):
 * Two lines across the box's centre lines, round caps.
 */
@Composable
fun PlusGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
    strokeWidth: Dp = 1.5.dp,
) {
    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        // Along the box's centre lines and across all of it, the round caps kept inside it.
        val lo = strokePx / 2f
        val hiX = size.width - lo
        val hiY = size.height - lo
        if (hiX <= lo || hiY <= lo) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f

        drawLine(
            color = color,
            start = Offset(lo, cy),
            end = Offset(hiX, cy),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(cx, lo),
            end = Offset(cx, hiY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Sheet grabber (design §11):
 * A 34×4dp pill in [InkGrabber], top padding 12, bottom 10.
 */
@Composable
fun Grabber(
    modifier: Modifier = Modifier,
    color: Color = InkGrabber,
) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 10.dp)
            .size(width = 34.dp, height = 4.dp)
            .background(color = color, shape = RoundedCornerShape(2.dp)),
    )
}

/**
 * Voice (design §2): five upright strokes on the 16-unit grid, the shape of
 * something said: at x 2, 5, 8, 11 and 14, centred on y 8, 4, 8, 12, 7 and 3
 * units tall; stroke 1.6u, round caps.
 */
@Composable
fun VoiceGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
) {
    Canvas(modifier = modifier) {
        val u = minOf(size.width, size.height) / 16f
        if (u <= 0f) return@Canvas
        val left = (size.width - 16f * u) / 2f
        val top = (size.height - 16f * u) / 2f
        VOICE_HEIGHTS.forEachIndexed { i, h ->
            val x = left + (2f + 3f * i) * u
            drawLine(
                color = color,
                start = Offset(x, top + (8f - h / 2f) * u),
                end = Offset(x, top + (8f + h / 2f) * u),
                strokeWidth = 1.6f * u,
                cap = StrokeCap.Round,
            )
        }
    }
}

private val VOICE_HEIGHTS = floatArrayOf(4f, 8f, 12f, 7f, 3f)

/** Play (design §2): a triangle on the 16-unit grid, (5, 3) (13, 8) (5, 13), filled, its corners rounded by a 1.4u stroke. */
@Composable
fun PlayGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
) {
    Canvas(modifier = modifier) {
        val u = minOf(size.width, size.height) / 16f
        if (u <= 0f) return@Canvas
        val triangle = Path().apply {
            moveTo(5f * u, 3f * u)
            lineTo(13f * u, 8f * u)
            lineTo(5f * u, 13f * u)
            close()
        }
        drawPath(triangle, color)
        drawPath(triangle, color, style = Stroke(width = 1.4f * u, join = StrokeJoin.Round))
    }
}

/** Pause (design §2): two bars on the 16-unit grid, 3u wide and 10u tall at x 4 and 9, from y 3, radius 1u. */
@Composable
fun PauseGlyph(
    modifier: Modifier = Modifier,
    color: Color = BoneDim,
) {
    Canvas(modifier = modifier) {
        val u = minOf(size.width, size.height) / 16f
        if (u <= 0f) return@Canvas
        for (x in floatArrayOf(4f, 9f)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x * u, 3f * u),
                size = Size(3f * u, 10f * u),
                cornerRadius = CornerRadius(u, u),
            )
        }
    }
}
