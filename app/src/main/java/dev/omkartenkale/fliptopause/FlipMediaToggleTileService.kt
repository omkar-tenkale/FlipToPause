
package dev.omkartenkale.fliptopause

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.omkartenkale.fliptopause.FlipMediaToggleService.Companion.START_ACTION
import dev.omkartenkale.fliptopause.FlipMediaToggleService.Companion.STOP_ACTION

open class FlipMediaToggleTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        syncTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        FlipState.init(this)
        val currentlyRunning = FlipState.isServiceRunning.value
        val nextRunningState = !currentlyRunning

        val intent = Intent(this, FlipMediaToggleService::class.java).apply {
            putExtra(if (nextRunningState) START_ACTION else STOP_ACTION, true)
        }

        try {
            if (nextRunningState && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            syncTileState(overrideRunning = nextRunningState)
        } catch (_: Exception) {
            // On SDK 34–37+, if starting a foreground service from the background is blocked
            // (e.g., missing notification permission or OEM background restriction), open the app.
            openAppFromTile()
        }
    }

    private fun openAppFromTile() {
        val openIntent = Intent(this, ViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(openIntent)
        }
    }

    private fun syncTileState(overrideRunning: Boolean? = null) {
        val tile = qsTile ?: return
        val strings = AppStrings.current
        val isRunning = overrideRunning ?: FlipState.isServiceRunning.value
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) strings.tileOn else strings.tileOff
        }
        tile.updateTile()
    }
}