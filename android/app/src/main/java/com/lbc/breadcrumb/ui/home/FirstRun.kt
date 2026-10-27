package com.lbc.breadcrumb.ui.home

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.embed.ModelStatus
import com.lbc.breadcrumb.ui.common.CrumbTrail
import com.lbc.breadcrumb.ui.common.LineBar
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberOnContainer
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.Ink
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder
import com.lbc.breadcrumb.ui.theme.InkOutline

/**
 * Whether the first-run screen has been seen (local-first L8): it shows on the
 * first launches until "Start keeping", and only while the model isn't ready.
 */
object FirstRun {
    private const val PREFS = "first_run"
    private const val SEEN = "seen"

    fun seen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
    }
}

/**
 * The model's first run (design §19), in place of the home screen: what the
 * model is and why it downloads, how far it has got, mobile data when it waits
 * for Wi-Fi, and the way on: everything but search by meaning works meanwhile.
 * [firstTime] says "Start keeping"; opened again from the model's bar, "Done".
 */
@Composable
fun FirstRunScreen(
    status: ModelStatus,
    firstTime: Boolean,
    onMobileData: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 22.dp),
        ) {
            val working = status !is ModelStatus.Ready &&
                !(status is ModelStatus.Waiting && status.why != ModelStatus.Why.STARTING)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CrumbTrail(Modifier.size(22.dp), walking = working)
                Text(stringResource(R.string.home_wordmark), style = serif(24.sp, lineHeight = 26.sp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(if (status is ModelStatus.Ready) R.string.first_run_ready else R.string.first_run_title),
                style = serif(30.sp, lineHeight = 34.sp),
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.first_run_body), style = sans(16.sp, BoneDim, lineHeight = 24.sp))
            Spacer(Modifier.height(28.dp))
            ModelProgress(status)
            if (status is ModelStatus.Waiting && status.why == ModelStatus.Why.WIFI) {
                var asked by remember { mutableStateOf(false) }
                if (!asked) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.first_run_mobile_data),
                        style = monoStyle(12.sp, BoneDim),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(InkElevated)
                            .border(1.dp, InkFieldBorder, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) {
                                asked = true
                                onMobileData()
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.first_run_hint), style = monoStyle(11.sp, InkOutline, lineHeight = 18.sp))
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(AmberBright)
                    .clickable(role = Role.Button, onClick = onContinue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (firstTime) R.string.first_run_start else R.string.first_run_done),
                    style = sans(15.sp, AmberOnContainer, FontWeight.SemiBold),
                )
            }
        }
    }
}

/**
 * The bar, 6dp tall, radius 3: InkElevated, AmberBright as far as it has got
 * (eased over 400 ms, read while drawing); under it, 10dp down, what it is doing
 * in mono 12sp BoneDim and "142 of 388 MB" in mono 12sp Bone.
 */
@Composable
private fun ModelProgress(status: ModelStatus) {
    val fraction by animateFloatAsState(ModelStatus.fraction(status), tween(400), label = "model")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(InkElevated, cornerRadius = radius)
        if (fraction > 0f) drawRoundRect(AmberBright, size = Size(size.width * fraction, size.height), cornerRadius = radius)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(ModelStatus.state(status), style = monoStyle(12.sp, BoneDim))
        Text(ModelStatus.amount(status), style = monoStyle(12.sp, Bone))
    }
}

/**
 * The model's bar over the search field (design §19), until the model is ready:
 * walking while it downloads; a tap opens the first-run screen again.
 */
@Composable
internal fun ModelBar(status: ModelStatus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    LineBar(
        line = ModelStatus.bar(status),
        walking = status is ModelStatus.Downloading || status == ModelStatus.Checking,
        onClick = onOpen,
        modifier = modifier,
    )
}
