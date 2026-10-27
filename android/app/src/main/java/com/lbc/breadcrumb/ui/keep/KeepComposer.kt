package com.lbc.breadcrumb.ui.keep

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.capture.Draft
import com.lbc.breadcrumb.capture.DraftFile
import com.lbc.breadcrumb.capture.DraftStore
import com.lbc.breadcrumb.capture.Keep
import com.lbc.breadcrumb.capture.MediaHelper
import com.lbc.breadcrumb.ui.common.ClearGlyph
import com.lbc.breadcrumb.ui.common.CrumbTrail
import com.lbc.breadcrumb.ui.common.DocumentGlyph
import com.lbc.breadcrumb.ui.common.VoiceGlyph
import com.lbc.breadcrumb.ui.common.Grabber
import com.lbc.breadcrumb.ui.common.PictureGlyph
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.home.sans
import com.lbc.breadcrumb.ui.home.serif
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberOnContainer
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkBorder
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder
import com.lbc.breadcrumb.ui.theme.InkMedia
import com.lbc.breadcrumb.ui.theme.InkOutline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the "+" composer (docs/design.md §13).
 *
 * Holds the draft through [DraftStore] (anything typed before the draft loads wins),
 * runs Keep on the application scope, and says when it is keeping and when the last keep failed.
 */
class KeepState(
    val context: Context,
    val scope: CoroutineScope,
    val store: DraftStore = DraftStore(context),
    private val keepAction: suspend (Context, Draft) -> Int = { ctx, draft -> Keep.keep(ctx, draft) },
) {
    var text: String by mutableStateOf("")
        private set

    var attachments: List<DraftFile> by mutableStateOf(emptyList())
        private set

    var keeping: Boolean by mutableStateOf(false)
        private set

    var lastFailed: Boolean by mutableStateOf(false)
        private set

    private var userEditedText: Boolean = false

    val hasDraft: Boolean
        get() = text.isNotBlank() || attachments.isNotEmpty()

    val canKeep: Boolean
        get() = hasDraft && !keeping

    init {
        scope.launch {
            val loaded = store.load()
            if (!userEditedText) {
                text = loaded.text
            }
            if (attachments.isEmpty()) {
                attachments = loaded.attachments
            } else {
                val currentUris = attachments.map { it.uri }.toSet()
                val merged = attachments + loaded.attachments.filter { it.uri !in currentUris }
                attachments = merged
                store.save(Draft(text = text, attachments = attachments))
            }
        }
    }

    fun onTextChange(newText: String) {
        userEditedText = true
        text = newText
        lastFailed = false
        store.save(Draft(text = newText, attachments = attachments))
    }

    fun addFiles(files: List<DraftFile>) {
        if (files.isEmpty()) return
        for (f in files) {
            store.takePermission(f.contentUri)
        }
        val currentUris = attachments.map { it.uri }.toSet()
        val toAdd = files.filter { it.uri !in currentUris }
        if (toAdd.isNotEmpty()) {
            attachments = attachments + toAdd
            lastFailed = false
            store.save(Draft(text = text, attachments = attachments))
        }
    }

    fun removeFile(file: DraftFile) {
        store.releasePermission(file.contentUri)
        attachments = attachments.filter { it.uri != file.uri }
        lastFailed = false
        store.save(Draft(text = text, attachments = attachments))
    }

    fun keep(onKept: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (keeping) return
        keeping = true
        lastFailed = false
        scope.launch {
            try {
                val currentDraft = Draft(text = text, attachments = attachments)
                val count = keepAction(context, currentDraft)
                if (count > 0) {
                    store.clear()
                    text = ""
                    attachments = emptyList()
                    userEditedText = false
                    keeping = false
                    lastFailed = false
                    onKept()
                } else {
                    keeping = false
                    lastFailed = true
                }
            } catch (_: Exception) {
                keeping = false
                lastFailed = true
            }
        }
    }
}

@Composable
fun rememberKeepState(
    context: Context = LocalContext.current,
    scope: CoroutineScope = rememberCoroutineScope(),
    store: DraftStore = remember(context) { DraftStore(context.applicationContext) },
): KeepState {
    return remember(context, scope, store) {
        KeepState(
            context = context.applicationContext,
            scope = scope,
            store = store,
        )
    }
}

/**
 * The "+" composer sheet content (docs/design.md §13).
 *
 * Grabber included.
 * Scrolling column:
 * - "Keep something" in serif 26sp lineHeight 30
 * - BasicTextField in serif 20sp lineHeight 28, cursor AmberBright, auto-focused after 320ms
 * - Picked files in horizontally scrolling Row
 * - Error line when last keep saved nothing
 * - Quiet line "bring in what's already on the phone"
 * Bottom row:
 * - Chips for photo and pdf, and the voice chip (a voice note, [onVoice])
 * - Keep button (48dp, radius 24, AmberBright when active, disabled with CrumbTrail while keeping)
 */
@Composable
fun ColumnScope.KeepComposer(
    state: KeepState,
    onBringIn: () -> Unit,
    onKept: () -> Unit,
    modifier: Modifier = Modifier,
    onVoice: () -> Unit = {},
) {
    val context = LocalContext.current

    // Photo picker launcher (images only, max 10)
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.map { uri ->
                val name = MediaHelper.getDisplayName(context, uri)
                DraftFile(
                    uri = uri.toString(),
                    name = name,
                    isPdf = false,
                )
            }
            state.addFiles(files)
        }
    }

    // PDF picker launcher (application/pdf, several at once)
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.map { uri ->
                val name = MediaHelper.getDisplayName(context, uri)
                DraftFile(
                    uri = uri.toString(),
                    name = name,
                    isPdf = true,
                )
            }
            state.addFiles(files)
        }
    }

    // Sheet Grabber
    Grabber(modifier = Modifier.align(Alignment.CenterHorizontally))

    // Scrolling Column
    Column(
        modifier = modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.keep_title),
            style = serif(26.sp, lineHeight = 30.sp),
        )

        // Text field box (at least 96dp tall)
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(320)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Ignore focus failure if unattached
            }
        }

        val hintText = if (state.attachments.isNotEmpty()) {
            stringResource(R.string.keep_hint_files)
        } else {
            stringResource(R.string.keep_hint_empty)
        }

        BasicTextField(
            value = state.text,
            onValueChange = { state.onTextChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .focusRequester(focusRequester),
            textStyle = serif(20.sp, color = Bone, lineHeight = 28.sp),
            cursorBrush = SolidColor(AmberBright),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = hintText,
                            style = serif(20.sp, color = InkOutline, lineHeight = 28.sp),
                        )
                    }
                    innerTextField()
                }
            },
        )

        // Picked files row
        if (state.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (file in state.attachments) {
                    PickedFileTile(
                        file = file,
                        onRemove = { state.removeFile(it) },
                    )
                }
            }
        }

        // When the last keep saved nothing
        if (state.lastFailed) {
            Text(
                text = stringResource(R.string.keep_failed),
                style = monoStyle(11.sp, color = AmberBright),
            )
        }

        // The quiet line "bring in what's already on the phone"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBringIn)
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.keep_bring_in),
                style = monoStyle(12.sp, color = InkOutline),
            )
        }
    }

    // Bottom row: chips, spacer, Keep button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "photo" chip
        PickerChip(
            label = stringResource(R.string.keep_chip_photo),
            onClick = {
                photoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        ) {
            PictureGlyph(
                modifier = Modifier.size(15.dp),
                color = BoneDim,
            )
        }

        // "pdf" chip
        PickerChip(
            label = stringResource(R.string.keep_chip_pdf),
            onClick = {
                pdfLauncher.launch(arrayOf("application/pdf"))
            },
        ) {
            DocumentGlyph(
                modifier = Modifier.size(width = 12.dp, height = 15.dp),
            )
        }

        // The voice chip: the glyph alone, so three chips and Keep fit a 360dp phone.
        val voiceLabel = stringResource(R.string.keep_chip_voice)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(InkElevated)
                .border(1.dp, InkFieldBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onVoice)
                .semantics { contentDescription = voiceLabel },
            contentAlignment = Alignment.Center,
        ) {
            VoiceGlyph(Modifier.size(16.dp), BoneDim)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Keep button
        val hasContentToKeep = state.text.isNotBlank() || state.attachments.isNotEmpty()
        KeepButton(
            lit = hasContentToKeep || state.keeping,
            keeping = state.keeping,
            enabled = hasContentToKeep && !state.keeping,
            onClick = { state.keep(onKept) },
        )
    }
}

/**
 * Keep (design §13): 48dp, radius 24, padding 22 horizontal; AmberBright when [lit], else
 * InkElevated; while [keeping], a walking CrumbTrail before the word. Disabled unless [enabled].
 */
@Composable
internal fun KeepButton(
    lit: Boolean,
    keeping: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val buttonBg = if (lit) AmberBright else InkElevated
    val buttonTextColor = if (lit) AmberOnContainer else InkOutline

    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(buttonBg)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) {
                    disabled()
                }
            }
            .then(
                if (enabled) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = keeping,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                CrumbTrail(
                    modifier = Modifier
                        .padding(end = 9.dp)
                        .size(15.dp),
                    walking = true,
                )
            }
            Text(
                text = stringResource(R.string.keep_action),
                style = sans(15.sp, color = buttonTextColor, weight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun PickerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(InkElevated)
            .border(1.dp, InkFieldBorder, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            glyph()
        }
        Text(
            text = label,
            style = monoStyle(12.sp, color = BoneDim),
        )
    }
}

@Composable
private fun PickedFileTile(
    file: DraftFile,
    onRemove: (DraftFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(InkMedia)
            .border(1.dp, InkBorder, RoundedCornerShape(10.dp)),
    ) {
        if (!file.isPdf) {
            val thumbnail by produceState<ImageBitmap?>(initialValue = null, file.contentUri) {
                value = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.loadThumbnail(
                            file.contentUri,
                            android.util.Size(220, 220),
                            null,
                        ).asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PictureGlyph(
                        modifier = Modifier.size(24.dp),
                        color = InkOutline,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                DocumentGlyph(
                    modifier = Modifier.size(width = 22.dp, height = 28.dp),
                )
                Text(
                    text = file.name ?: stringResource(R.string.capture_pdf),
                    style = monoStyle(8.sp, color = BoneDim),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Top end remove button: 22dp circle in black α0.6 holding an 8dp × in Bone (1.5dp): "Remove"
        val removeDescription = stringResource(R.string.keep_remove)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    role = Role.Button,
                    onClick = { onRemove(file) },
                )
                .semantics {
                    contentDescription = removeDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            ClearGlyph(
                modifier = Modifier.size(8.dp),
                color = Bone,
                strokeWidth = 1.5.dp,
            )
        }
    }
}
