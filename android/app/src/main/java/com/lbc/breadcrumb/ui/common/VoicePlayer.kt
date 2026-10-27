package com.lbc.breadcrumb.ui.common

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberOnContainer
import com.lbc.breadcrumb.ui.theme.InkBorder
import com.lbc.breadcrumb.ui.theme.InkOutline
import com.lbc.breadcrumb.voice.AudioLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How long a voice note plays, read off the main thread; null until known, or when it can't be. */
@Composable
fun rememberAudioLength(memory: Memory): State<Long?> {
    val originals = (LocalContext.current.applicationContext as BreadcrumbApp).originals
    return produceState<Long?>(null, memory.id, memory.localUri) {
        value = withContext(Dispatchers.IO) { originals.fileFor(memory)?.let(AudioLength::of) }
    }
}

/**
 * Plays a voice note in place (design §11): a round play button, then the track
 * with where it has got to, over the time gone and the whole length. One
 * MediaPlayer while it is shown, made at the first play; it pauses when the app
 * leaves the screen and goes when the player does.
 */
@Composable
fun VoicePlayer(file: File, modifier: Modifier = Modifier) {
    val length by produceState<Long?>(null, file) { value = withContext(Dispatchers.IO) { AudioLength.of(file) } }
    val player = remember(file) { Playback(file) }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    // Where it has got to, every frame while it plays.
    LaunchedEffect(player, player.playing.value) {
        while (player.playing.value) {
            withFrameMillis { player.tick() }
        }
    }
    val total = length ?: player.duration
    val second by remember(player) { derivedStateOf { player.position.longValue / 1000 } }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val playing = player.playing.value
        val label = stringResource(if (playing) R.string.voice_pause else R.string.voice_play)
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AmberBright)
                .clickable { player.toggle() }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (playing) PauseGlyph(Modifier.size(18.dp), AmberOnContainer) else PlayGlyph(Modifier.size(18.dp), AmberOnContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Track(
                fraction = { if (total != null && total > 0) (player.position.longValue.toFloat() / total).coerceIn(0f, 1f) else 0f },
                onSeek = { f -> total?.let { player.seek((f * it).toLong()) } },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(AudioLength.clock(second * 1000), style = monoStyle(11.sp, InkOutline))
                Text(total?.let(AudioLength::clock).orEmpty(), style = monoStyle(11.sp, InkOutline))
            }
        }
    }
}

/** The track: a 3dp line in InkBorder, amber as far as it has played, with a 10dp knob there. A tap or a drag seeks. */
@Composable
private fun Track(fraction: () -> Float, onSeek: (Float) -> Unit) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) { detectTapGestures { onSeek(it.x / size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        val y = size.height / 2f
        val stroke = 3.dp.toPx()
        val knob = 5.dp.toPx()
        val x = knob + (size.width - 2 * knob) * fraction()
        drawLine(InkBorder, Offset(knob, y), Offset(size.width - knob, y), stroke, StrokeCap.Round)
        drawLine(AmberBright, Offset(knob, y), Offset(x, y), stroke, StrokeCap.Round)
        drawCircle(AmberBright, knob, Offset(x, y))
    }
}

/** A MediaPlayer made at the first play, and what the player draws from it. */
private class Playback(private val file: File) {
    val playing = mutableStateOf(false)
    val position = mutableLongStateOf(0L)
    private var player: MediaPlayer? = null

    val duration: Long? get() = player?.duration?.toLong()?.takeIf { it > 0 }

    fun toggle() = if (playing.value) pause() else play()

    private fun play() {
        val p = player ?: try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(file.path)
                prepare()
                setOnCompletionListener {
                    playing.value = false
                    position.longValue = 0
                    it.seekTo(0)
                }
            }.also { player = it }
        } catch (_: Exception) {
            return
        }
        if (position.longValue > 0) p.seekTo(position.longValue.toInt())
        p.start()
        playing.value = true
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        playing.value = false
    }

    fun seek(to: Long) {
        position.longValue = to
        player?.seekTo(to.toInt())
    }

    fun tick() {
        player?.let { position.longValue = it.currentPosition.toLong() }
    }

    fun release() {
        player?.release()
        player = null
        playing.value = false
    }
}
