package com.lbc.breadcrumb.ui.capture

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.math.roundToInt

sealed class CaptureState {
    object Saving : CaptureState()
    /** [memories] saved of the [total] offered: fewer when some could not be read or kept. */
    data class Saved(val memories: List<Memory>, val total: Int = memories.size) : CaptureState() {
        val count: Int get() = memories.size
    }
    data class Failed(val reason: String) : CaptureState()
    object Removed : CaptureState()
}

/**
 * The sheet a capture shows over the app the share came from. One transition
 * drives the scrim and the sheet both ways: the dim fades in as the sheet
 * rises, and on Done, Back, a tap on the scrim, a swipe, or after Undo, both
 * leave together. [onDismiss] runs only once the exit has played and one clear
 * frame has drawn: finishing sooner hid the window in the frame the exit
 * started, so the exit never showed.
 */
@Composable
fun CaptureSheet(
    state: CaptureState,
    // The note lives with the activity, not here: it writes the note when the
    // person leaves (onStop), when this sheet never hears of it.
    note: String,
    onNoteChange: (String) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showNoteField by remember { mutableStateOf(false) }
    // Not while saving: a shared content:// URI is readable only while the activity lives.
    val canClose = state !is CaptureState.Saving

    val shown = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { shown.targetState = true }
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            shown.targetState = false
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            snapshotFlow { shown.isIdle && !shown.currentState }.first { it }
            withFrameNanos { } // one frame drawn with nothing on it
            onDismiss()
        }
    }
    // Read only while laying out and drawing, never in composition, so neither
    // the transition nor a drag recomposes the sheet every frame.
    val shownFraction by rememberTransition(shown, label = "capture sheet").animateFloat(
        transitionSpec = { tween(SHEET_MILLIS) },
        label = "shown",
    ) { if (it) 1f else 0f }

    BackHandler {
        if (canClose) close()
    }

    // Swipe down to close: the sheet follows the finger and the scrim fades
    // with it; past a third of the sheet, or on a downward fling, it closes,
    // and otherwise it springs back.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val flingVelocity = with(LocalDensity.current) { FLING_VELOCITY.toPx() }

    suspend fun settleDrag(velocity: Float) {
        val goes = sheetHeight > 0 && (
            velocity > flingVelocity ||
                (velocity >= -flingVelocity && dragOffset > sheetHeight / 3f)
            )
        val target = if (goes) sheetHeight.toFloat() else 0f
        animate(dragOffset, target, initialVelocity = velocity) { value, _ -> dragOffset = value }
        if (goes) close()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The scrim is the sheet's sibling, behind it, not its parent: a
        // parent's clickable also hears every tap inside the sheet, so tapping
        // the preview or the text would close it.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val dragged = if (sheetHeight > 0) (dragOffset / sheetHeight).coerceIn(0f, 1f) else 0f
                    alpha = shownFraction * (1f - dragged)
                }
                .background(CaptureScrim.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canClose && !closing,
                ) { close() }
        )
        Surface(
            // draggable sits outside the placement below, so the gesture is
            // measured in a frame that does not move with the sheet it moves.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = canClose && !closing,
                    state = rememberDraggableState { delta ->
                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                    },
                    onDragStopped = { velocity -> settleDrag(velocity) },
                )
                // Below its place by the part of the transition still to play,
                // then by the drag: in and out with the scrim, from its own height.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        val hidden = (1f - shownFraction) * placeable.height
                        placeable.placeRelative(0, (hidden + dragOffset).roundToInt())
                    }
                },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Edge to edge (targetSdk 36 on Android 15+, and enableEdgeToEdge
                    // on older ones), adjustResize no longer lifts Compose content:
                    // the sheet keeps clear of the navigation bar, then the keyboard.
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp),
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(InkGrabber)
                    )
                }
            when (state) {
                is CaptureState.Saving -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AmberBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                is CaptureState.Saved -> {
                    // Preview
                    SavedPreview(state.memories)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title: "Saved", "Saved 3", or "Saved 2 of 3" when some could not be kept
                    Text(
                        text = when {
                            state.count < state.total -> "Saved ${state.count} of ${state.total}"
                            state.count == 1 -> "Saved"
                            else -> "Saved ${state.count}"
                        },
                        style = TextStyle(
                            fontFamily = Serif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Note field (C6)
                    AnimatedVisibility(visible = showNoteField) {
                        Column {
                            BasicTextField(
                                value = note,
                                onValueChange = onNoteChange,
                                textStyle = TextStyle(
                                    fontFamily = Sans,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(AmberBright),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        InkFieldBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                                    .heightIn(min = 48.dp),
                                decorationBox = { innerTextField ->
                                    if (note.isEmpty()) {
                                        Text(
                                            "Add a note…",
                                            style = TextStyle(
                                                fontFamily = Sans,
                                                fontSize = 14.sp,
                                                color = BoneDim
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    if (!showNoteField) {
                        Text(
                            text = "add a note",
                            style = TextStyle(
                                fontFamily = Mono,
                                fontSize = 12.sp,
                                color = InkLabel
                            ),
                            modifier = Modifier
                                .clickable { showNoteField = true }
                                .padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Undo / Done
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onUndo() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = BoneDim
                            )
                        ) {
                            Text(
                                "Undo",
                                style = TextStyle(
                                    fontFamily = Sans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        Button(
                            onClick = { close() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberBright,
                                contentColor = AmberOnContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Done",
                                style = TextStyle(
                                    fontFamily = Sans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }

                is CaptureState.Removed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Removed",
                            style = TextStyle(
                                fontFamily = Serif,
                                fontWeight = FontWeight.Normal,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    LaunchedEffect(Unit) {
                        delay(800)
                        close()
                    }
                }

                is CaptureState.Failed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.reason,
                                style = TextStyle(
                                    fontFamily = Sans,
                                    fontSize = 14.sp,
                                    color = BoneDim
                                )
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { close() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InkElevated,
                                    contentColor = Bone
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Close",
                                    style = TextStyle(
                                        fontFamily = Sans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Preview composables
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SavedPreview(memories: List<Memory>) {
    if (memories.isEmpty()) return
    val first = memories.first()

    when {
        // Single image — large, follows its shape
        memories.size == 1 && first.type == MemoryType.IMAGE -> {
            SingleImagePreview(first)
        }
        // Multiple images — row of square tiles, +N on the third
        memories.size > 1 && memories.all { it.type == MemoryType.IMAGE } -> {
            MultiImagePreview(memories)
        }
        // Single PDF
        first.type == MemoryType.PDF -> {
            PdfCardPreview(first)
        }
        // Link
        first.type == MemoryType.LINK -> {
            LinkCardPreview(first)
        }
        // Text
        first.type == MemoryType.TEXT -> {
            TextPreview(first)
        }
        // Fallback for mixed types
        else -> {
            memories.firstOrNull { it.type == MemoryType.IMAGE }?.let {
                SingleImagePreview(it)
            } ?: memories.firstOrNull()?.let {
                TextPreview(it)
            }
        }
    }
}

@Composable
private fun SingleImagePreview(memory: Memory) {
    val uri = memory.localUri
    if (uri != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(Uri.parse(uri))
                .build(),
            contentDescription = "Saved image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InkMedia)
        )
    }
}

@Composable
private fun MultiImagePreview(memories: List<Memory>) {
    val visible = memories.take(3)
    val overflow = memories.size - 3

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(visible) { index, memory ->
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(InkMedia)
            ) {
                val uri = memory.localUri
                if (uri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(uri))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // "+N" overlay on the third tile
                if (index == 2 && overflow > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Ink.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+$overflow",
                            style = TextStyle(
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = Bone
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCardPreview(memory: Memory) {
    val url = memory.rawText ?: ""
    val host = try {
        Uri.parse(url.trim().lines().firstOrNull { it.startsWith("http") } ?: url).host ?: ""
    } catch (_: Exception) { "" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InkElevated)
            .padding(16.dp)
    ) {
        // Site
        if (host.isNotBlank()) {
            Text(
                text = host.removePrefix("www."),
                style = TextStyle(
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    color = InkLabel
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        // Title
        if (!memory.title.isNullOrBlank()) {
            Text(
                text = memory.title,
                style = TextStyle(
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Bone
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        // URL
        Text(
            text = url.take(120),
            style = TextStyle(
                fontFamily = Mono,
                fontSize = 11.sp,
                color = BoneDim
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PdfCardPreview(memory: Memory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InkElevated)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // PDF icon area
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AmberContainerDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "PDF",
                style = TextStyle(
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = AmberBright
                )
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memory.title ?: "Document",
                style = TextStyle(
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Bone
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "PDF",
                style = TextStyle(
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    color = InkLabel
                )
            )
        }
    }
}

@Composable
private fun TextPreview(memory: Memory) {
    val text = memory.rawText ?: ""
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InkElevated)
            .padding(16.dp)
    ) {
        Text(
            text = text.take(300),
            style = TextStyle(
                fontFamily = Sans,
                fontSize = 14.sp,
                color = Bone,
                lineHeight = 20.sp
            ),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A downward fling this fast closes the sheet from anywhere, as Material's sheets do. */
private val FLING_VELOCITY = 125.dp

/** How long the sheet and the scrim take to come in, and to go. */
private const val SHEET_MILLIS = 280

/** The scrim's dim when the sheet is fully up. */
private const val SCRIM_ALPHA = 0.7f
