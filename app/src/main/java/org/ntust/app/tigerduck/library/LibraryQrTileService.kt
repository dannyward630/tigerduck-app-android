package org.ntust.app.tigerduck.library

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.ntust.app.tigerduck.AppLink
import org.ntust.app.tigerduck.activityIntent
import org.ntust.app.tigerduck.pendingIntent

/**
 * The Quick Settings tile that opens the library QR, beside [LibraryQrShortcut]. An action, not a
 * switch, so it always shows off. A locked phone is unlocked first: the QR is the student's pass.
 */
class LibraryQrTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::openLibraryQr) else openLibraryQr()
    }

    private fun openLibraryQr() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(AppLink.Library.pendingIntent(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(AppLink.Library.activityIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
