package com.lbc.breadcrumb.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.imports.BACKUP_TYPES
import com.lbc.breadcrumb.imports.ChoiceCard
import com.lbc.breadcrumb.ui.common.Grabber
import com.lbc.breadcrumb.ui.common.LineBar
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.home.serif
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.BoneDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Everything kept (design §20), the content of a sheet opened from the
 * masthead's count: how much there is, then two choices, saving a backup to a
 * file the person picks, and restoring one; the backup's line under them.
 * Picking closes the sheet at once ([onPicked]); the work goes on, on the
 * application's scope, and its line shows over the search field.
 */
@Composable
fun ColumnScope.KeptSheet(kept: String, onPicked: () -> Unit) {
    val app = LocalContext.current.applicationContext as BreadcrumbApp
    val progress by app.backups.progress.collectAsState()
    val size by produceState<Long?>(null) {
        value = withContext(Dispatchers.IO) { app.originals.files().sumOf { it.length() } }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            app.backups.save(uri)
            onPicked()
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            app.backups.restore(uri)
            onPicked()
        }
    }

    Grabber(Modifier.align(Alignment.CenterHorizontally))
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.kept_title), style = serif(26.sp, lineHeight = 30.sp))
            Text(
                size?.let { stringResource(R.string.kept_summary, kept, BackupProgress.megabytes(it)) } ?: kept,
                style = monoStyle(12.sp, BoneDim),
            )
        }
        ChoiceCard(
            label = stringResource(R.string.backup_save),
            detail = stringResource(R.string.backup_save_detail),
            onClick = { save.launch(BackupFormat.fileName(LocalDate.now().toString())) },
        )
        ChoiceCard(
            label = stringResource(R.string.backup_restore),
            detail = stringResource(R.string.backup_restore_detail),
            onClick = { restore.launch(BACKUP_TYPES) },
        )
        BackupProgress.line(progress)?.let { Text(it, style = monoStyle(12.sp, AmberBright)) }
        Spacer(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(20.dp),
        )
    }
}

/**
 * A backup's line over the search field (design §20), in the import bar's pill:
 * walking while it runs; a tap, or 6 s after it finishes, clears it.
 */
@Composable
fun BackupBar(backups: Backups, modifier: Modifier = Modifier) {
    val progress by backups.progress.collectAsState()
    LaunchedEffect(progress) {
        if (progress?.finished == true) {
            delay(6_000)
            backups.forget()
        }
    }
    LineBar(
        line = BackupProgress.line(progress),
        walking = progress is BackupProgress.Saving || progress is BackupProgress.Restoring,
        onClick = backups::forget,
        modifier = modifier,
    )
}
