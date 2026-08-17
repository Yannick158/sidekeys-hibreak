package com.sidekeys.hibreak.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings

/**
 * Toggles Android's Battery Saver.
 *
 * Preferred path: hold [Manifest.permission.WRITE_SECURE_SETTINGS] (granted once
 * via Shizuku or adb) and write `low_power` directly — then it works forever
 * without Shizuku running. Fallback: run the same write through Shizuku live.
 */
object PowerSaver {

    fun isEnabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Can we toggle right now — either natively or through live Shizuku? */
    fun canToggle(context: Context): Boolean =
        hasWriteSecureSettings(context) ||
            (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted())

    private fun writeDirect(context: Context, enabled: Boolean): Boolean = runCatching {
        Settings.Global.putInt(context.contentResolver, "low_power", if (enabled) 1 else 0)
    }.getOrDefault(false)

    /** Returns true if the new state was applied. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (hasWriteSecureSettings(context) && writeDirect(context, enabled)) return true
        if (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()) {
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
     * One-time: grant ourselves WRITE_SECURE_SETTINGS via Shizuku so Battery
     * Saver can be toggled natively afterwards, even without Shizuku running.
     * Returns true if we hold the permission afterwards.
     */
    fun grantPermanentAccess(context: Context): Boolean {
        if (hasWriteSecureSettings(context)) return true
        if (!ShizukuShell.isAvailable() || !ShizukuShell.isPermissionGranted()) return false
        ShizukuShell.run("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        return hasWriteSecureSettings(context)
    }
}
