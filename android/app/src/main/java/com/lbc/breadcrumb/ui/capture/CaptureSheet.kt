package com.lbc.breadcrumb.ui.capture

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import java.io.File

sealed class CaptureState {
    object Saving : CaptureState()
    data class Saved(val count: Int, val memories: List<Memory>) : CaptureState()
    data class Failed(val reason: String) : CaptureState()
    object Removed : CaptureState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(
    state: CaptureState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onNote: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isDismissing by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showNoteField by remember { mutableStateOf(false) }

    val handleDismiss: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            // Deliver note if present
            if (noteText.isNotBlank()) {
                onNote?.invoke(noteText.trim())
            }
            scope.launch {
                onDismiss()
            }
        }
    }

    val canDismiss = state !is CaptureState.Saving
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    fun animateAndDismiss() {
        visible = false
        // For simplicity, just dismiss immediately (since the activity finishes anyway)
        handleDismiss()
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (canDismiss) {
            animateAndDismiss()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // The scrim is the sheet's sibling, behind it, not its parent: a
        // parent's clickable also hears every tap inside the sheet, so tapping
        // the preview or the text would close it.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(CaptureScrim.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canDismiss,
                ) { animateAndDismiss() }
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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

                    // Title: "Saved" or "Saved 3"
                    Text(
                        text = if (state.count == 1) "Saved" else "Saved ${state.count}",
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
                                value = noteText,
                                onValueChange = { noteText = it },
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
                                    if (noteText.isEmpty()) {
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
                            onClick = { handleDismiss() },
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
                        handleDismiss()
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
                                onClick = { handleDismiss() },
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
