package com.lbc.breadcrumb.ui.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.lbc.breadcrumb.imports.BulkImporter
import com.lbc.breadcrumb.imports.ImportPanel
import com.lbc.breadcrumb.ui.keep.KeepComposer
import com.lbc.breadcrumb.ui.keep.KeepState
import com.lbc.breadcrumb.ui.keep.VoicePanel

/** What the "+" sheet shows (design §13, §14). */
internal enum class PlusPanel {
    /** Capture's composer: something written, and a photo or a PDF to go with it. */
    COMPOSER,

    /** Reading's import panel: what is already on the phone. */
    IMPORT,

    /** A voice note, recorded as soon as it opens. */
    VOICE,
    ;

    companion object {
        /**
         * The "+" opens on the composer, never where the sheet was last left; while
         * nothing is kept, straight on the import panel, which is where to start (§7).
         */
        fun opening(nothingKept: Boolean): PlusPanel = if (nothingKept) IMPORT else COMPOSER
    }
}

/**
 * The "+" sheet's content: the composer, or after its quiet line the import panel,
 * or after its voice chip the voice panel. [onPanel] switches between them;
 * [onClose] closes the sheet, once something is kept or picked to bring in.
 */
@Composable
internal fun ColumnScope.PlusSheet(
    panel: PlusPanel,
    keepState: KeepState,
    importer: BulkImporter,
    onPanel: (PlusPanel) -> Unit,
    onClose: () -> Unit,
) {
    when (panel) {
        PlusPanel.COMPOSER -> KeepComposer(
            state = keepState,
            onBringIn = { onPanel(PlusPanel.IMPORT) },
            onKept = onClose,
            onVoice = { onPanel(PlusPanel.VOICE) },
        )
        PlusPanel.VOICE -> VoicePanel(
            onBack = { onPanel(PlusPanel.COMPOSER) },
            onKept = onClose,
        )
        PlusPanel.IMPORT -> ImportPanel(
            onBack = { onPanel(PlusPanel.COMPOSER) },
            onPicked = onClose,
            importer = importer,
        )
    }
}
