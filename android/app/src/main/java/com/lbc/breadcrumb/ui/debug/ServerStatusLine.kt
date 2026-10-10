package com.lbc.breadcrumb.ui.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.net.ServerClient
import com.lbc.breadcrumb.net.ServerStatus

/**
 * Whether the phone reaches the server, and if not, what to fix: the
 * forward missing, the server not running, or another server on the port.
 * Checked when the list opens, and again on a tap.
 */
@Composable
internal fun ServerStatusLine(server: ServerClient) {
    var check by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<ServerStatus?>(null) }
    LaunchedEffect(check) {
        status = null
        status = server.checkHealth()
    }
    val text = when (val s = status) {
        null -> stringResource(R.string.server_checking)
        ServerStatus.Reachable -> stringResource(R.string.server_reachable)
        ServerStatus.Refused -> stringResource(R.string.server_refused)
        ServerStatus.ClosedWithoutReply -> stringResource(R.string.server_closed)
        is ServerStatus.OtherServer -> stringResource(R.string.server_other, s.detail)
        ServerStatus.TimedOut -> stringResource(R.string.server_timed_out)
        is ServerStatus.Unreachable -> stringResource(R.string.server_unreachable, s.reason)
    }
    Text(
        text,
        style = chrome,
        color = if (status == ServerStatus.Reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = status != null) { check += 1 }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
