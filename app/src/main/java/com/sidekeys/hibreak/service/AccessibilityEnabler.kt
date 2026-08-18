package com.sidekeys.hibreak.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Enables SideKeys' own accessibility service without the manual
 * "Allow restricted settings" → toggle dance that Android 13+ requires for
 * sideloaded apps after every update.
 *
 * Needs WRITE_SECURE_SETTINGS, granted once via adb (see [PowerSaver.GRANT_COMMAND]).
 */
object AccessibilityEnabler {

    private const val ENABLED_SERVICES = "enabled_accessibility_services"
    private const val ACCESSIBILITY_ON = "accessibility_enabled"

    fun component(context: Context): String =
        ComponentName(context, KeyInterceptorService::class.java).flattenToString()

    /** True if we hold the permission needed to do this. */
    fun canEnable(context: Context): Boolean = PowerSaver.hasWriteSecureSettings(context)

    /**
     * Enables — or, if already listed, RESTARTS — the service. Returns true on success.
     *
     * Toggling off→on matters: after Bigme's task manager force-stops the app,
     * the service still appears "enabled" in Settings but is not running, and
     * Android does not rebind a stopped service until it is toggled. Rewriting
     * the same value would be a no-op, so we remove and re-add ourselves.
     */
    fun enable(context: Context): Boolean {
        if (!canEnable(context)) return false
        val comp = component(context)
        return runCatching {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, ENABLED_SERVICES).orEmpty()
            val without = withoutService(current, comp)
            if (without != current) {
                Settings.Secure.putString(resolver, ENABLED_SERVICES, without)
            }
            Settings.Secure.putString(resolver, ENABLED_SERVICES, mergeService(without, comp))
            Settings.Secure.putInt(resolver, ACCESSIBILITY_ON, 1)
            true
        }.getOrDefault(false)
    }

    private fun withoutService(current: String, comp: String): String {
        if (current.isBlank() || current == "null") return ""
        return current.split(':').filter { it.isNotBlank() && it != comp }.joinToString(":")
    }

    private fun mergeService(current: String, comp: String): String {
        if (current.isBlank() || current == "null") return comp
        val parts = current.split(':').filter { it.isNotBlank() }
        return if (parts.contains(comp)) current else (parts + comp).joinToString(":")
    }
}
