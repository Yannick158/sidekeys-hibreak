package com.sidekeys.hibreak.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Enables SideKeys' own accessibility service without the manual
 * "Allow restricted settings" → toggle dance that Android 13+ requires for
 * sideloaded apps after every update.
 *
 * Uses the WRITE_SECURE_SETTINGS permission (if granted) or Shizuku to write the
 * accessibility secure settings directly — the same effect the settings UI has,
 * but in one tap and not subject to the restricted-settings UI gate.
 */
object AccessibilityEnabler {

    private const val ENABLED_SERVICES = "enabled_accessibility_services"
    private const val ACCESSIBILITY_ON = "accessibility_enabled"

    fun component(context: Context): String =
        ComponentName(context, KeyInterceptorService::class.java).flattenToString()

    /** True if we can do this right now (native permission or live Shizuku). */
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
        // Native path via WRITE_SECURE_SETTINGS.
        if (PowerSaver.hasWriteSecureSettings(context)) {
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
        // Shizuku path: same off→on toggle in shell.
        if (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()) {
            val script = """
                cur=${'$'}(settings get secure $ENABLED_SERVICES)
                comp='$comp'
                [ "${'$'}cur" = "null" ] && cur=""
                # remove ourselves (if present)
                without=${'$'}(echo "${'$'}cur" | tr ':' '\n' | grep -v -x -F "${'$'}comp" | grep -v '^${'$'}' | paste -sd: -)
                if [ "${'$'}without" != "${'$'}cur" ]; then
                  settings put secure $ENABLED_SERVICES "${'$'}without"
                fi
                # re-add
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
