package com.lbc.breadcrumb.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.home.sans
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder
import com.lbc.breadcrumb.voice.Recording
import com.lbc.breadcrumb.voice.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a recording is. */
enum class VoicePhase {
    /** The microphone is being asked for. */
    ASKING,
    RECORDING,

    /** Stopped, and being kept. */
    KEEPING,

    /** The microphone was refused. */
    NO_MICROPHONE,

    /** Nothing could be recorded, or kept. */
    FAILED,
}

/**
 * A recording being made: the recorder, how loud it has been, and for how long.
 * [keep] decides what becomes of it (a voice note of its own, a note said over a
 * memory), on the application's scope. Whatever is recording when its
 * composable goes, or the app leaves the screen, is kept, not lost.
 */
@Stable
class VoiceState(
    private val app: BreadcrumbApp,
    private val keep: suspend (Recording) -> Boolean,
) {
    var phase by mutableStateOf(VoicePhase.ASKING)
        private set

    /** The latest loudness samples, 0 to 1, oldest first. */
    val levels = mutableStateListOf<Float>()
    val elapsed = mutableLongStateOf(0L)

    /** Called once it is kept, however it came to be: a button, the time limit, the composable going. */
    var onKept: () -> Unit = {}

    private val recorder = VoiceRecorder(app)
    private var startedAt = 0L

    fun begin() {
        if (phase == VoicePhase.RECORDING || phase == VoicePhase.KEEPING) return
        levels.clear()
        elapsed.longValue = 0
        if (recorder.start(onLimit = ::finish)) {
            startedAt = System.currentTimeMillis()
            phase = VoicePhase.RECORDING
        } else {
            phase = VoicePhase.FAILED
        }
    }

    fun refused() {
        phase = VoicePhase.NO_MICROPHONE
    }

    /** One loudness sample, and the time. */
    fun sample() {
        levels += recorder.level()
        if (levels.size > MAX_SAMPLES) levels.removeAt(0)
        elapsed.longValue = System.currentTimeMillis() - startedAt
    }

    /** Stops, and keeps what was recorded. */
    fun finish() {
        if (phase != VoicePhase.RECORDING) return
        val recording = recorder.stop()
        if (recording == null) {
            phase = VoicePhase.FAILED
            return
        }
        phase = VoicePhase.KEEPING
        val done = onKept
        app.applicationScope.launch {
            val kept = keep(recording)
            withContext(Dispatchers.Main) {
                if (kept) done() else phase = VoicePhase.FAILED
            }
        }
    }

    fun discard() {
        recorder.discard()
        phase = VoicePhase.ASKING
    }

    companion object {
        const val MAX_SAMPLES = 160
    }
}

/**
 * A [VoiceState] that records as soon as it is shown, once the microphone is
 * allowed (asked for the first time), samples how loud it is every 70 ms, and
 * keeps what it has when the app leaves the screen or it stops being shown.
 * [onKept] once it is kept. Returns it, and how to ask for the microphone again.
 */
@Composable
fun rememberRecording(onKept: () -> Unit, keep: suspend (Recording) -> Boolean): Pair<VoiceState, () -> Unit> {
    val context = LocalContext.current
    val app = context.applicationContext as BreadcrumbApp
    val keeping by rememberUpdatedState(keep)
    val state = remember { VoiceState(app) { keeping(it) } }
    val kept by rememberUpdatedState(onKept)
    state.onKept = { kept() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.begin() else state.refused()
    }
    LaunchedEffect(Unit) {
        if (hasMicrophone(context)) state.begin() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(state.phase) {
        while (state.phase == VoicePhase.RECORDING) {
            state.sample()
            delay(SAMPLE_MS)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.finish() }
    DisposableEffect(state) { onDispose { state.finish() } }

    val askAgain = {
        val activity = context.findActivity()
        if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Refused for good: only the app's settings can allow it now.
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    return state to askAgain
}

/**
 * How loud it has been (design §13): a bar a sample, 3dp wide with 3dp between,
 * newest at the right, centred, as tall as it was loud (3dp at the least): the
 * newest in AmberBright, the rest in Bone, fading to the left. Read while drawing.
 */
@Composable
fun Trace(levels: List<Float>, height: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val bar = 3.dp.toPx()
        val step = 6.dp.toPx()
        val fits = (size.width / step).toInt()
        val shown = levels.takeLast(fits)
        val middle = size.height / 2f
        shown.forEachIndexed { i, level ->
            val x = size.width - (shown.size - i) * step
            val tall = maxOf(bar, level * size.height)
            val age = (shown.size - 1 - i).toFloat() / fits
            drawRoundRect(
                color = if (i == shown.lastIndex) AmberBright else Bone.copy(alpha = 0.85f - 0.6f * age),
                topLeft = Offset(x, middle - tall / 2f),
                size = Size(bar, tall),
                cornerRadius = CornerRadius(bar / 2f, bar / 2f),
            )
        }
    }
}

/** A 7dp amber dot that breathes while it records: alpha 1 to 0.3, 700 ms each way, in graphicsLayer. */
@Composable
fun RecordingDot() {
    val pulse = rememberInfiniteTransition(label = "recording")
    val alpha by pulse.animateFloat(1f, 0.3f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Box(
        Modifier
            .size(7.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(AmberBright),
    )
}

/** Refused the microphone: why it is wanted, and a chip to allow it. */
@Composable
fun MicrophoneRefused(onAllow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.voice_needs_microphone), style = sans(15.sp, lineHeight = 22.sp))
        Text(
            stringResource(R.string.voice_allow_microphone),
            style = monoStyle(12.sp, BoneDim),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(InkElevated)
                .border(1.dp, InkFieldBorder, RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClick = onAllow)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

private fun hasMicrophone(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val SAMPLE_MS = 70L
