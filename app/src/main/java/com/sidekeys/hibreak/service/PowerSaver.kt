package com.sidekeys.hibreak.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings

/**
 * Toggles Android's Battery Saver.
 *
 * Requires [Manifest.permission.WRITE_SECURE_SETTINGS], which a normal app can
 * never request at runtime — it is granted once from a computer:
 *
 *     adb shell pm grant com.sidekeys.hibreak android.permission.WRITE_SECURE_SETTINGS
 *
 * Once granted it is permanent (until the app is uninstalled). Without it the
 * UI falls back to opening the Battery Saver settings page.
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

    /** Whether Battery Saver can actually be toggled right now. */
    fun canToggle(context: Context): Boolean = hasWriteSecureSettings(context)

    /** Returns true if the new state was applied. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        return runCatching {
            Settings.Global.putInt(context.contentResolver, "low_power", if (enabled) 1 else 0)
        }.getOrDefault(false)
    }

    /** Toggles Battery Saver; returns the new state, or null if it failed. */
    fun toggle(context: Context): Boolean? {
        val target = !isEnabled(context)
        return if (setEnabled(context, target)) target else null
    }
}
