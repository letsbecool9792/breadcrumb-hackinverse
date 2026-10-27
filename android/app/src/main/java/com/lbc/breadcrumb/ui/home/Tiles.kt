package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.ui.common.DocumentGlyph
import com.lbc.breadcrumb.ui.common.VoiceGlyph
import com.lbc.breadcrumb.ui.common.rememberAudioLength
import com.lbc.breadcrumb.ui.common.rememberThumbnail
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberContainerDark
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.Ink
import com.lbc.breadcrumb.ui.theme.InkBorder
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkMedia
import com.lbc.breadcrumb.ui.theme.InkOutline
import com.lbc.breadcrumb.voice.AudioLength
import java.util.Locale
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(10.dp)
private const val THUMBNAIL_PX = 360

/**
 * One memory in the mosaic (design §6): by kind, a picture that is all picture,
 * a document's first page, a link's host, a note's own words, or what was said
 * in a voice note.
 */
@Composable
internal fun MemoryTile(memory: Memory, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(InkElevated)
            .border(1.dp, InkBorder, TileShape)
            .clickable(onClick = onClick),
    ) {
        when (memory.type) {
            MemoryType.IMAGE -> PictureTile(memory, now)
            MemoryType.PDF -> DocumentTile(memory, now)
            MemoryType.LINK -> LinkTile(memory, now)
            MemoryType.TEXT -> NoteTile(memory, now)
            MemoryType.AUDIO -> VoiceTile(memory, now)
        }
    }
}

/** A tile's meta line ("2d · whatsapp"). */
@Composable
private fun Meta(memory: Memory, now: Long, modifier: Modifier = Modifier, color: Color = InkOutline) {
    Text(
        ResultText.tileMeta(memory, now),
        style = metaStyle.copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * All picture, captioned over a shade. Height follows the picture's shape,
 * between 150 and 270 dp, and the picture is anchored at its top: a screenshot
 * is recognised by its top.
 */
@Composable
private fun PictureTile(memory: Memory, now: Long) {
    val thumbnail = rememberThumbnail(memory, THUMBNAIL_PX)
    val aspect = thumbnail?.let { it.height.toFloat() / it.width }
    Box(
        Modifier
            .fillMaxWidth()
            .pictureHeight(aspect)
            .background(InkMedia),
    ) {
        TravellingPicture(memory.id) { travel ->
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
        val caption = ResultText.firstLine(memory.title ?: memory.rawText ?: "") ?: memory.summary
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.6f), Ink.copy(alpha = 0.94f))))
                .padding(start = 11.dp, end = 11.dp, top = 30.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!caption.isNullOrBlank()) {
                Text(
                    caption,
                    style = sans(13.sp, weight = FontWeight.Medium, lineHeight = 17.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Meta(memory, now, color = BoneDim)
        }
    }
}

/** The first page on a quiet ground, and under it the title. */
@Composable
private fun DocumentTile(memory: Memory, now: Long) {
    val thumbnail = rememberThumbnail(memory, THUMBNAIL_PX)
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(108.dp)
                .background(InkMedia)
                .padding(start = 13.dp, end = 13.dp, top = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            TravellingPicture(memory.id) { travel ->
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        modifier = travel
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                } else {
                    DocumentGlyph(Modifier.size(width = 40.dp, height = 52.dp))
                }
            }
        }
        Column(
            Modifier.padding(start = 11.dp, end = 11.dp, top = 9.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                ResultText.title(memory),
                style = sans(14.sp, weight = FontWeight.Medium, lineHeight = 19.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Meta(memory, now)
        }
    }
}

/** The page's preview picture when it has one, then the host in a small amber mark, then the title. */
@Composable
private fun LinkTile(memory: Memory, now: Long) {
    val host = memory.rawText?.let(ResultText::host)
    val preview = rememberThumbnail(memory, THUMBNAIL_PX)
    Column {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(InkMedia),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (host != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(AmberContainerDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            host.first().uppercase(Locale.ROOT),
                            style = monoStyle(9.sp, AmberBright, weight = FontWeight.Medium),
                        )
                    }
                    Text(
                        host,
                        style = monoStyle(10.sp, BoneDim),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                ResultText.title(memory),
                style = sans(14.sp, weight = FontWeight.Medium, lineHeight = 19.sp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Meta(memory, now)
        }
    }
}

/** A note is its own words, under a small amber quotation mark. */
@Composable
private fun NoteTile(memory: Memory, now: Long) {
    Column(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("“", style = sans(34.sp, AmberBright, FontWeight.Medium, lineHeight = 26.sp))
        Text(
            memory.rawText ?: memory.title ?: "",
            style = sans(15.sp, lineHeight = 21.sp),
            maxLines = 7,
            overflow = TextOverflow.Ellipsis,
        )
        Meta(memory, now, Modifier.padding(top = 3.dp))
    }
}

/**
 * A voice note: the voice mark in a small amber circle and how long it plays,
 * then what was said; "listening…" until it is heard, and its title ("Voice
 * note") when nothing could be made out.
 */
@Composable
private fun VoiceTile(memory: Memory, now: Long) {
    val length by rememberAudioLength(memory)
    Column(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(AmberContainerDark),
                contentAlignment = Alignment.Center,
            ) {
                VoiceGlyph(Modifier.size(14.dp), AmberBright)
            }
            Text(length?.let(AudioLength::clock).orEmpty(), style = monoStyle(10.sp, BoneDim))
        }
        val said = memory.extractedText?.trim()
        when {
            said == null -> Text(stringResource(R.string.voice_listening), style = monoStyle(11.sp, InkOutline))
            said.isNotEmpty() -> Text(
                said,
                style = sans(15.sp, lineHeight = 21.sp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            else -> Text(
                ResultText.title(memory),
                style = sans(15.sp, BoneDim, lineHeight = 21.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Meta(memory, now)
    }
}

/** A height from the width it is given and the picture's shape ([MosaicMath.pictureTileHeightDp]). */
private fun Modifier.pictureHeight(aspect: Float?): Modifier = layout { measurable, constraints ->
    val density = this.density
    val width = constraints.maxWidth
    val height = (MosaicMath.pictureTileHeightDp(width / density, aspect) * density).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}
