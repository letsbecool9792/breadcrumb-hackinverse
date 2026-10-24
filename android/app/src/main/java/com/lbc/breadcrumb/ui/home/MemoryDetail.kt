package com.lbc.breadcrumb.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.open.OriginalAction
import com.lbc.breadcrumb.ui.common.BinGlyph
import com.lbc.breadcrumb.ui.common.DocumentGlyph
import com.lbc.breadcrumb.ui.common.Grabber
import com.lbc.breadcrumb.ui.common.LeavesTheAppGlyph
import com.lbc.breadcrumb.ui.common.ShareGlyph
import com.lbc.breadcrumb.ui.common.rememberThumbnail
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberOnContainer
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkLabel
import com.lbc.breadcrumb.ui.theme.InkMedia
import com.lbc.breadcrumb.ui.theme.InkOutline
import java.util.Locale

/**
 * What the sheet does with a memory (design §11, §12). Held apart from the
 * drawing so the sheet can be shown, and tested, without an app behind it.
 */
internal class DetailActions(
    /** The one main action: open the original, open the link, copy the text. */
    val main: OriginalAction,
    val onMain: () -> Unit,
    /** Null when there is nothing to share. */
    val onShare: (() -> Unit)?,
    val onDelete: () -> Unit,
    /** Write the note; null clears it. */
    val onNote: (String?) -> Unit,
)

/**
 * The detail of a memory, as the content of a [SheetLayer]. The row is live, so
 * a summary copied back while it is open appears in place.
 *
 * Top to bottom: the grabber; a scrolling column of the artifact and, a beat
 * behind it, the heading, the person's note, where it came from and the text
 * found; then the actions.
 */
@Composable
internal fun ColumnScope.MemoryDetail(
    memory: Memory,
    now: Long,
    visibility: AnimatedVisibilityScope,
    actions: DetailActions,
) {
    Grabber(Modifier.align(Alignment.CenterHorizontally))
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
    ) {
        Artifact(memory, visibility, onOpen = actions.onMain)
        Column(
            with(visibility) {
                Modifier
                    .animateEnterExit(
                        enter = fadeIn(tween(320, delayMillis = 140)) + slideInVertically(tween(420, delayMillis = 100)) { it / 6 },
                        exit = fadeOut(tween(120)),
                    )
            }
                .padding(start = 22.dp, end = 22.dp, top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Heading(memory)
            NoteSection(memory, onNote = actions.onNote)
            Provenance(memory, now)
            FoundText(memory)
        }
    }
    ActionRow(actions)
}

@Composable
private fun Artifact(memory: Memory, visibility: AnimatedVisibilityScope, onOpen: () -> Unit) {
    when (memory.type) {
        MemoryType.IMAGE -> PictureArtifact(memory, visibility, onOpen)
        MemoryType.PDF -> PageArtifact(memory, visibility, onOpen)
        MemoryType.LINK -> LinkCard(memory, onOpen)
        MemoryType.TEXT, MemoryType.AUDIO -> NoteCard(memory)
    }
}

/** The picture itself, as tall as it is, between 160 dp and 42% of the window. */
@Composable
private fun PictureArtifact(memory: Memory, visibility: AnimatedVisibilityScope, onOpen: () -> Unit) {
    val thumbnail = rememberThumbnail(memory, 1080)
    val aspect = thumbnail?.let { it.height.toFloat() / it.width }
    val windowHeight = LocalConfiguration.current.screenHeightDp.toFloat()
    val maxHeight = maxOf(windowHeight * 0.42f, 200f)
    Box(
        Modifier
            .fillMaxWidth()
            .pictureArtifactHeight(aspect, maxHeight)
            .background(InkMedia)
            .clickable(onClick = onOpen),
    ) {
        with(visibility) {
            SheetPicture(memory.id) { travel ->
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = travel.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                }
            }
        }
    }
}

/** A PDF's first page, held on a quiet ground. */
@Composable
private fun PageArtifact(memory: Memory, visibility: AnimatedVisibilityScope, onOpen: () -> Unit) {
    val thumbnail = rememberThumbnail(memory, 900)
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(InkMedia)
            .clickable(onClick = onOpen)
            .padding(start = 28.dp, end = 28.dp, top = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        with(visibility) {
            SheetPicture(memory.id) { travel ->
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = travel
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                } else {
                    DocumentGlyph(Modifier.size(width = 72.dp, height = 94.dp))
                }
            }
        }
    }
}

/** A link: its host, its title in the serif, and the address under it. */
@Composable
private fun LinkCard(memory: Memory, onOpen: () -> Unit) {
    val host = memory.rawText?.let(ResultText::host)
    // The page's preview picture, when it has one, over the card.
    rememberThumbnail(memory, 1080)?.let { preview ->
        Image(
            bitmap = preview,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(onClick = onOpen),
            contentScale = ContentScale.Crop,
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(InkMedia)
            .clickable(onClick = onOpen)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (host != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(AmberBright))
                Text(host, style = monoStyle(12.sp, AmberBright))
            }
        }
        Text(ResultText.title(memory), style = serif(23.sp, lineHeight = 28.sp))
        if (!memory.title.isNullOrBlank() && !memory.rawText.isNullOrBlank()) {
            SelectionContainer {
                Text(
                    memory.rawText,
                    style = monoStyle(11.sp, InkOutline, lineHeight = 17.sp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A note: its words, whole, in the serif. */
@Composable
private fun NoteCard(memory: Memory) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(InkMedia)
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        SelectionContainer {
            Text(memory.rawText ?: memory.title ?: "", style = serif(20.sp, lineHeight = 28.sp))
        }
    }
}

@Composable
private fun Heading(memory: Memory) {
    val heading = DetailText.heading(memory)
    if (heading.isEmpty) return
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        heading.title?.let { Text(it, style = serif(27.sp, lineHeight = 32.sp)) }
        heading.caption?.let { SelectionContainer { Text(it, style = sans(15.sp, lineHeight = 22.sp)) } }
        heading.summary?.let { SelectionContainer { Text(it, style = sans(15.sp, BoneDim, lineHeight = 22.sp)) } }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(Locale.ROOT), style = monoStyle(10.sp, InkLabel, letterSpacing = 1.2.sp), modifier = modifier)
}

/**
 * The person's own words about it, the only thing editable here. A tap starts
 * editing; Done, or the sheet closing mid-edit, writes it.
 */
@Composable
private fun NoteSection(memory: Memory, onNote: (String?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(memory.note.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentNote by rememberUpdatedState(memory.note)
    val write by rememberUpdatedState(onNote)

    fun commit() {
        val edit = DetailText.noteEdit(draft, currentNote)
        if (edit is DetailText.NoteEdit.Write) write(edit.note)
    }

    // Closing the sheet mid-edit still writes what was typed.
    val editingNow by rememberUpdatedState(editing)
    DisposableEffect(Unit) { onDispose { if (editingNow) commit() } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            editing -> {
                SectionLabel(stringResource(R.string.detail_your_note))
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(InkElevated)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = sans(16.sp, lineHeight = 22.sp),
                        cursorBrush = SolidColor(AmberBright),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    if (draft.isEmpty()) {
                        Text(stringResource(R.string.capture_note_hint), style = sans(16.sp, InkOutline, lineHeight = 22.sp))
                    }
                }
                Text(
                    stringResource(R.string.capture_done),
                    style = sans(14.sp, AmberBright, FontWeight.SemiBold),
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            commit()
                            editing = false
                            keyboard?.hide()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            !memory.note.isNullOrBlank() -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(stringResource(R.string.detail_your_note))
                    Text(
                        stringResource(R.string.detail_edit),
                        style = monoStyle(10.sp, AmberBright),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { draft = memory.note.orEmpty(); editing = true },
                    )
                }
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { draft = memory.note.orEmpty(); editing = true },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(2.dp).fillMaxSize().clip(RoundedCornerShape(1.dp)).background(AmberBright))
                    Text(memory.note.orEmpty(), style = sans(16.sp, lineHeight = 23.sp))
                }
            }
            else -> Row(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { draft = ""; editing = true },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", style = monoStyle(14.sp, AmberBright))
                Text(stringResource(R.string.capture_add_note), style = monoStyle(12.sp, BoneDim))
            }
        }
    }
}

/** Where it came from and when, receding. */
@Composable
private fun Provenance(memory: Memory, now: Long) {
    val rows = DetailText.provenance(memory, now)
    Column {
        HorizontalDivider(color = InkElevated)
        rows.forEachIndexed { index, (kind, value) ->
            if (index > 0) HorizontalDivider(color = InkElevated.copy(alpha = 0.7f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(
                        when (kind) {
                            DetailText.Provenance.SAVED -> R.string.detail_saved
                            DetailText.Provenance.FROM -> R.string.detail_from
                            DetailText.Provenance.TAKEN -> R.string.detail_taken
                            DetailText.Provenance.STATUS -> R.string.detail_status
                        },
                    ),
                    style = monoStyle(11.sp, InkOutline),
                )
                Text(value, style = monoStyle(11.sp, BoneDim))
            }
        }
    }
}

/** What the phone read and what the model saw, as plain text. */
@Composable
private fun FoundText(memory: Memory) {
    DetailText.foundText(memory).forEach { (kind, text) ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(
                stringResource(
                    when (kind) {
                        DetailText.Found.TEXT_FOUND -> R.string.detail_text_found
                        DetailText.Found.FROM_THE_PAGE -> R.string.detail_from_the_page
                        DetailText.Found.WHAT_IT_SHOWS -> R.string.detail_what_it_shows
                    },
                ),
            )
            SelectionContainer { Text(text, style = monoStyle(11.sp, InkOutline, lineHeight = 18.sp)) }
        }
    }
}

/** The one main action, and share and delete as round buttons beside it. */
@Composable
private fun ActionRow(actions: DetailActions) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val main = actions.main
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (main.isEnabled) AmberBright else InkElevated)
                .clickable(enabled = main.isEnabled, onClick = actions.onMain),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (main.isEnabled) AmberOnContainer else InkOutline
            Text(stringResource(main.labelRes), style = sans(15.sp, color, FontWeight.SemiBold))
            if (main.leavesApp) {
                Box(Modifier.width(8.dp))
                LeavesTheAppGlyph(Modifier.size(15.dp), color)
            }
        }
        actions.onShare?.let { share ->
            RoundAction(stringResource(R.string.detail_action_share), onClick = share) { ShareGlyph(Modifier.size(18.dp), BoneDim) }
        }
        RoundAction(
            stringResource(R.string.detail_delete),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                actions.onDelete()
            },
        ) { BinGlyph(Modifier.size(18.dp), BoneDim) }
    }
}

@Composable
private fun RoundAction(description: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(InkElevated)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/** The picture's height: as tall as it is wide-for-shape, between 160 dp and [maxDp] (260 dp until loaded). */
private fun Modifier.pictureArtifactHeight(aspect: Float?, maxDp: Float): Modifier =
    layout { measurable, constraints ->
        val density = this.density
        val width = constraints.maxWidth
        val dp = if (aspect == null || aspect <= 0f) 260f else (width / density * aspect).coerceIn(160f, maxDp)
        val height = (dp * density).toInt()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }
