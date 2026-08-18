package com.sidekeys.hibreak.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings

/**
 * Toggles Android's Battery Saver.
 *
 * Android offers no API for this to normal apps (`setPowerSaveModeEnabled` needs
 * DEVICE_POWER, `setDynamicPowerSaveHint` needs POWER_SAVER — both signature
 * permissions), so it goes through [Manifest.permission.WRITE_SECURE_SETTINGS].
 * There are two ways to obtain it, both one-time and both entirely local:
 *
 *  1. From a computer:  [GRANT_COMMAND]
 *  2. From the phone:   via Shizuku, see [grantPermanentAccess]
 *
 * If the permission is missing but Shizuku is running, the write is routed
 * through Shizuku directly. Nothing here reads screen content.
 */
object PowerSaver {

    /** The one-time setup command, shown in the UI so it can be copied. */
    const val GRANT_COMMAND =
        "adb shell pm grant com.sidekeys.hibreak android.permission.WRITE_SECURE_SETTINGS"

    fun isEnabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun shizukuReady(): Boolean =
        ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()

    /** Whether Battery Saver can actually be toggled right now. */
    fun canToggle(context: Context): Boolean = hasWriteSecureSettings(context) || shizukuReady()

    /**
     * Returns true if the new state was applied. The native path is instant and
     * safe on the main thread; the Shizuku path blocks and must not be.
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (hasWriteSecureSettings(context)) {
            val ok = runCatching {
                Settings.Global.putInt(context.contentResolver, "low_power", if (enabled) 1 else 0)
            }.getOrDefault(false)
            if (ok) return true
        }
        if (shizukuReady()) {
            return ShizukuShell.run("settings put global low_power ${if (enabled) 1 else 0}").ok
        }
        return false
    }

    /** Toggles Battery Saver; returns the new state, or null if it failed. */
    fun toggle(context: Context): Boolean? {
        val target = !isEnabled(context)
        return if (setEnabled(context, target)) target else null
    }

    /**
     * One-time, via Shizuku: grant ourselves WRITE_SECURE_SETTINGS so the toggle
     * keeps working even when Shizuku is not running (it needs restarting after
     * every reboot). Blocking — call off the main thread.
     */
    fun grantPermanentAccess(context: Context): Boolean {
        if (hasWriteSecureSettings(context)) return true
        if (!shizukuReady()) return false
        ShizukuShell.run("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        return hasWriteSecureSettings(context)
    }
}
