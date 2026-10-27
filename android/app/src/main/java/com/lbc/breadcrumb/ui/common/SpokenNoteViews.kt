package com.lbc.breadcrumb.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.home.sans
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkOutline
import com.lbc.breadcrumb.voice.AudioLength
import com.lbc.breadcrumb.voice.Recording
import java.io.File

/**
 * "say it" (design §11, §15): the voice glyph 13dp in AmberBright, then the words
 * in mono 12sp BoneDim, spacing 7, no ripple. Starts saying a note.
 */
@Composable
fun SayIt(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceGlyph(Modifier.size(13.dp), AmberBright)
        Text(stringResource(R.string.spoken_say), style = monoStyle(12.sp, BoneDim))
    }
}

/**
 * Saying a note, in place, as soon as it is shown (design §11, §15): a box, radius
 * 12, InkElevated, padding 14/12, spacing 10: the breathing dot and "recording",
 * the time at the end; the trace, 36dp tall; then at the end "discard" (mono 12sp
 * BoneDim) and "Done" (sans 14sp AmberBright SemiBold). Done, the sheet going, or
 * the app leaving keeps it through [keep], then [onDone]; discard is [onCancel].
 */
@Composable
fun SayANote(
    keep: suspend (Recording) -> Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (state, askAgain) = rememberRecording(onDone, keep)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InkElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.phase == VoicePhase.NO_MICROPHONE) {
            MicrophoneRefused(onAllow = askAgain)
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.phase == VoicePhase.RECORDING) {
                        RecordingDot()
                        Text(stringResource(R.string.voice_recording), style = monoStyle(12.sp, BoneDim))
                    }
                    if (state.phase == VoicePhase.FAILED) {
                        Text(
                            stringResource(R.string.voice_failed),
                            style = monoStyle(11.sp, AmberBright),
                            modifier = Modifier.clickable { state.begin() },
                        )
                    }
                }
                val seconds by remember { derivedStateOf { state.elapsed.longValue / 1000 } }
                Text(AudioLength.clock(seconds * 1000), style = monoStyle(12.sp, Bone))
            }
            Trace(state.levels, height = 36.dp)
        }
        Row(
            Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.voice_discard),
                style = monoStyle(12.sp, BoneDim),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = state.phase != VoicePhase.KEEPING) {
                        state.discard()
                        onCancel()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            Text(
                stringResource(R.string.capture_done),
                style = sans(14.sp, AmberBright, FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = state.phase == VoicePhase.RECORDING, onClick = state::finish)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * A note said over a memory, kept (design §11): its player, then "listening…"
 * until it is heard (mono 11sp InkOutline), and at the end "remove" (mono 10sp
 * BoneDim), which takes the recording away and leaves what was said in the note.
 */
@Composable
fun SpokenNotePlayer(file: File, heard: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VoicePlayer(file, Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (heard) "" else stringResource(R.string.voice_listening),
                style = monoStyle(11.sp, InkOutline),
            )
            Text(
                stringResource(R.string.spoken_remove),
                style = monoStyle(10.sp, BoneDim),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}
