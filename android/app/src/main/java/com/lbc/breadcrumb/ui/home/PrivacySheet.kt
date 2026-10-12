package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.common.Grabber
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.AmberOnContainer
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder
import com.lbc.breadcrumb.ui.theme.InkOutline

/**
 * What leaves the phone (local first): two switches, opened from "privacy"
 * under the wordmark. Search runs on the phone either way; with both off the
 * app makes no call to the server but the one that tells it to forget, and
 * while that hasn't landed the second switch says "forgetting…".
 */
@Composable
internal fun ColumnScope.PrivacySheet(
    cloudParses: Boolean,
    onCloudParses: (Boolean) -> Unit,
    cloudReads: Boolean,
    onCloudReads: (Boolean) -> Unit,
    forgetting: Boolean = false,
) {
    Grabber(Modifier.align(Alignment.CenterHorizontally))
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.privacy_title), style = serif(26.sp, lineHeight = 30.sp))
        Text(stringResource(R.string.privacy_lead), style = sans(15.sp, BoneDim, lineHeight = 22.sp))
        Choice(
            label = stringResource(R.string.privacy_parses),
            detail = stringResource(if (cloudParses) R.string.privacy_parses_on else R.string.privacy_parses_off),
            checked = cloudParses,
            onChange = onCloudParses,
        )
        Choice(
            label = stringResource(R.string.privacy_reads),
            detail = stringResource(
                when {
                    forgetting -> R.string.privacy_reads_forgetting
                    cloudReads -> R.string.privacy_reads_on
                    else -> R.string.privacy_reads_off
                },
            ),
            checked = cloudReads,
            onChange = onCloudReads,
        )
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun Choice(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = sans(15.sp, Bone, weight = FontWeight.SemiBold))
            Text(detail, style = monoStyle(11.sp, BoneDim, lineHeight = 16.sp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmberOnContainer,
                checkedTrackColor = AmberBright,
                uncheckedThumbColor = InkOutline,
                uncheckedTrackColor = InkElevated,
                uncheckedBorderColor = InkFieldBorder,
            ),
        )
    }
}
