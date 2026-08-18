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

    /** True if we can do this right now — natively or through running Shizuku. */
    fun canEnable(context: Context): Boolean = PowerSaver.canToggle(context)

    /**
     * Enables — or, if already listed, RESTARTS — the service. Returns true on success.
     *
     * Toggling off→on matters: after Bigme's task manager force-stops the app,
     * the service still appears "enabled" in Settings but is not running, and
     * Android does not rebind a stopped service until it is toggled. Rewriting
     * the same value would be a no-op, so we remove and re-add ourselves.
     */
    fun enable(context: Context): Boolean {
        val comp = component(context)
        if (PowerSaver.hasWriteSecureSettings(context)) {
            val ok = runCatching {
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
            if (ok) return true
        }
        // Shizuku path: same off→on toggle, run as shell.
        if (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()) {
            val script = """
                cur=${'$'}(settings get secure $ENABLED_SERVICES)
                comp='$comp'
                [ "${'$'}cur" = "null" ] && cur=""
                without=${'$'}(echo "${'$'}cur" | tr ':' '\n' | grep -v -x -F "${'$'}comp" | grep -v '^${'$'}' | paste -sd: -)
                if [ "${'$'}without" != "${'$'}cur" ]; then
                  settings put secure $ENABLED_SERVICES "${'$'}without"
                fi
                if [ -z "${'$'}without" ]; then new="${'$'}comp"; else new="${'$'}without:${'$'}comp"; fi
                settings put secure $ENABLED_SERVICES "${'$'}new"
                settings put secure $ACCESSIBILITY_ON 1
                echo ok
            """.trimIndent()
            return ShizukuShell.run(script).stdout.trim().endsWith("ok")
        }
        return false
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
