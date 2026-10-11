package com.lbc.breadcrumb.imports

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.R
import com.lbc.breadcrumb.ui.common.Grabber
import com.lbc.breadcrumb.ui.home.monoStyle
import com.lbc.breadcrumb.ui.home.sans
import com.lbc.breadcrumb.ui.home.serif
import com.lbc.breadcrumb.ui.theme.AmberBright
import com.lbc.breadcrumb.ui.theme.Bone
import com.lbc.breadcrumb.ui.theme.BoneDim
import com.lbc.breadcrumb.ui.theme.InkElevated
import com.lbc.breadcrumb.ui.theme.InkFieldBorder

/**
 * The import panel in the "+" sheet in place of the composer (docs/design.md §14).
 *
 * Grabber; a scrolling Column, padding 22 horizontal, top 6, spacing 14:
 * - A Row: "Bring in" in serif 26sp lineHeight 30, and at the right "back" in mono 12sp BoneDim,
 *   padding 10/6, radius 14 (back to the composer).
 * - Three choices, each a card: full width, radius 14, InkElevated, border InkFieldBorder,
 *   padding 16/14, spacing 3, a label in sans 15sp Bone SemiBold over detail in mono 11sp BoneDim:
 *   - "A whole folder" -> folder picker opening at primary:Pictures/Screenshots -> importFolder.
 *   - "Only these pictures" -> photo picker, images only -> importVisualMedia.
 *   - "PDFs, or a bookmarks export" -> document picker for application/pdf and text/html ->
 *     PDFs to importPdfs, an HTML file to importBookmarks.
 * - The import's line in mono 12sp AmberBright, when there is one.
 * - A navigationBarsPadding spacer of 20dp at the foot.
 * - Picking closes the sheet at once ([onPicked]); the import runs on the application's scope.
 */
@Composable
fun ColumnScope.ImportPanel(
    onBack: () -> Unit,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
    importer: BulkImporter = (LocalContext.current.applicationContext as BreadcrumbApp).importer,
) {
    val context = LocalContext.current
    val progress by importer.progress.collectAsState()

    // 1. Whole folder picker (opening at primary:Pictures/Screenshots)
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            importer.importFolder(uri)
            onPicked()
        }
    }

    // 2. Photo picker (images only)
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            importer.importVisualMedia(uris)
            onPicked()
        }
    }

    // 3. Document picker (application/pdf and text/html)
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val htmlUri = uris.firstOrNull { uri ->
                val mime = context.contentResolver.getType(uri) ?: ""
                mime.equals("text/html", ignoreCase = true) ||
                    uri.lastPathSegment?.endsWith(".html", ignoreCase = true) == true ||
                    uri.lastPathSegment?.endsWith(".htm", ignoreCase = true) == true
            }
            val pdfUris = uris.filter { it != htmlUri }
            if (pdfUris.isNotEmpty()) {
                importer.importPdfs(pdfUris)
            }
            if (htmlUri != null) {
                importer.importBookmarks(htmlUri)
            }
            onPicked()
        }
    }

    Grabber()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.import_title),
                style = serif(26.sp, lineHeight = 30.sp),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.import_back),
                    style = monoStyle(12.sp, BoneDim),
                )
            }
        }

        // Three choices
        ChoiceCard(
            label = stringResource(R.string.import_choice_folder),
            detail = stringResource(R.string.import_choice_folder_detail),
            onClick = {
                val initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Pictures/Screenshots",
                )
                folderLauncher.launch(initialUri)
            },
        )

        ChoiceCard(
            label = stringResource(R.string.import_choice_photos),
            detail = stringResource(R.string.import_choice_photos_detail),
            onClick = {
                photoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )

        ChoiceCard(
            label = stringResource(R.string.import_choice_files),
            detail = stringResource(R.string.import_choice_files_detail),
            onClick = {
                fileLauncher.launch(arrayOf("application/pdf", "text/html"))
            },
        )

        // The import's line in mono 12sp AmberBright when there is one
        val line = importLine(progress)
        if (line != null) {
            Text(
                text = line,
                style = monoStyle(12.sp, AmberBright),
            )
        }

        // Footer spacer: 20dp with navigationBarsPadding
        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(20.dp),
        )
    }
}

@Composable
private fun ChoiceCard(
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkElevated)
            .border(1.dp, InkFieldBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = sans(15.sp, color = Bone, weight = FontWeight.SemiBold),
        )
        Text(
            text = detail,
            style = monoStyle(11.sp, BoneDim),
        )
    }
}
