package com.lbc.breadcrumb.ui.keep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.capture.Keep
import com.lbc.breadcrumb.ui.common.Grabber
import com.lbc.breadcrumb.ui.common.MicrophoneRefused
import com.lbc.breadcrumb.ui.common.RecordingDot
import com.lbc.breadcrumb.ui.common.Trace
import com.lbc.breadcrumb.ui.common.VoicePhase
import com.lbc.breadcrumb.ui.common.rememberRecording
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.home.serif
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.voice.AudioLength

/**
 * The "+" sheet's voice panel (design §13): recording starts as it opens, once
 * the microphone is allowed. "Say it", and "discard" (or "back" when nothing is
 * recording); the trace of how loud it has been, newest at the right; a pulsing
 * dot, "recording" and the time; then Keep. Swiping the sheet away, or leaving
 * the app, keeps what was recorded.
 */
@Composable
fun ColumnScope.VoicePanel(
    onBack: () -> Unit,
    onKept: () -> Unit,
) {
    val context = LocalContext.current
    val (state, askAgain) = rememberRecording(onKept) { Keep.keepVoice(context, it) }

    Grabber(Modifier.align(Alignment.CenterHorizontally))

    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.voice_title), style = serif(26.sp, lineHeight = 30.sp))
            val recording = state.phase == VoicePhase.RECORDING
            Text(
                stringResource(if (recording) R.string.voice_discard else R.string.voice_back),
                style = monoStyle(12.sp, BoneDim),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = state.phase != VoicePhase.KEEPING) {
                        state.discard()
                        onBack()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (state.phase == VoicePhase.NO_MICROPHONE) {
            MicrophoneRefused(onAllow = askAgain)
        } else {
            Trace(state.levels, height = 88.dp)
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
                }
                val seconds by remember { derivedStateOf { state.elapsed.longValue / 1000 } }
                Text(AudioLength.clock(seconds * 1000), style = monoStyle(13.sp, Bone))
            }
            if (state.phase == VoicePhase.FAILED) {
                Text(
                    stringResource(R.string.voice_failed),
                    style = monoStyle(11.sp, AmberBright),
                    modifier = Modifier.clickable { state.begin() },
                )
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        KeepButton(
            lit = state.phase == VoicePhase.RECORDING || state.phase == VoicePhase.KEEPING,
            keeping = state.phase == VoicePhase.KEEPING,
            enabled = state.phase == VoicePhase.RECORDING,
            onClick = state::finish,
        )
    }
}
