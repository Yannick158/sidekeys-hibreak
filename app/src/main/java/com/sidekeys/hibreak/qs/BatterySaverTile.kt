package com.sidekeys.hibreak.qs

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sidekeys.hibreak.service.PowerSaver
import com.sidekeys.hibreak.service.ShizukuShell

/**
 * A Quick Settings tile that toggles Battery Saver — the tile the HiBreak Pro's
 * stock quick settings is missing. Toggling goes through Shizuku, so if Shizuku
 * isn't set up the tile stays unavailable and points the user to accessibility
 * settings instead of failing silently.
 */
class BatterySaverTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        // Native path: instant, no Shizuku needed.
        if (PowerSaver.hasWriteSecureSettings(this)) {
            val target = !PowerSaver.isEnabled(this)
            PowerSaver.setEnabled(this, target)
            applyState(ready = true, on = target)
            return
        }
        // Live Shizuku path (blocks): run off the main thread.
        if (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()) {
            Thread {
                val newState = PowerSaver.toggle(this)
                Handler(Looper.getMainLooper()).post {
                    if (newState != null) applyState(ready = true, on = newState) else refresh()
                }
            }.start()
            return
        }
        // No privilege: open Battery Saver settings so the user can toggle it.
        openSettings()
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The Intent overload of startActivityAndCollapse throws on API 34+.
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            runCatching { startActivityAndCollapse(pending) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { startActivityAndCollapse(intent) }
        }
    }

    private fun refresh() {
        applyState(PowerSaver.canToggle(this), PowerSaver.isEnabled(this))
    }

    private fun applyState(ready: Boolean, on: Boolean) {
        val tile = qsTile ?: return
        tile.state = when {
            !ready -> Tile.STATE_UNAVAILABLE
            on -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
