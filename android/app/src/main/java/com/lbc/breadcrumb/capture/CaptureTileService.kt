package com.lbc.breadcrumb.capture

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lbc.breadcrumb.R

/** The Quick Settings tile: one tap saves whatever is on the clipboard. */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // An action, not a toggle: never shown as "on", and its second line
        // says what a tap does.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            subtitle = getString(R.string.tile_subtitle)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // From the lock screen, unlock first: the clipboard is the person's,
        // and the capture sheet needs an unlocked phone to show.
        if (isLocked) unlockAndRun(::openCapture) else openCapture()
    }

    private fun openCapture() {
        val intent = Intent(this, ClipboardCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
