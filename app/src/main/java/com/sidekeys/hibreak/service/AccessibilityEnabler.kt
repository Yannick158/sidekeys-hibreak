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

    /** Enables the service. Returns true on success. */
    fun enable(context: Context): Boolean {
        val comp = component(context)
        // Native path via WRITE_SECURE_SETTINGS.
        if (PowerSaver.hasWriteSecureSettings(context)) {
            return runCatching {
                val resolver = context.contentResolver
                val current = Settings.Secure.getString(resolver, ENABLED_SERVICES).orEmpty()
                val updated = mergeService(current, comp)
                Settings.Secure.putString(resolver, ENABLED_SERVICES, updated)
                Settings.Secure.putInt(resolver, ACCESSIBILITY_ON, 1)
                true
            }.getOrDefault(false)
        }
        // Shizuku path.
        if (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()) {
            val script = """
                cur=${'$'}(settings get secure $ENABLED_SERVICES)
                comp='$comp'
                case ":${'$'}cur:" in
                  *":${'$'}comp:"*) new="${'$'}cur" ;;
                  *) if [ "${'$'}cur" = "null" ] || [ -z "${'$'}cur" ]; then new="${'$'}comp"; else new="${'$'}cur:${'$'}comp"; fi ;;
                esac
                settings put secure $ENABLED_SERVICES "${'$'}new"
                settings put secure $ACCESSIBILITY_ON 1
                echo ok
            """.trimIndent()
            return ShizukuShell.run(script).stdout.trim().endsWith("ok")
        }
        return false
    }

    private fun mergeService(current: String, comp: String): String {
        if (current.isBlank() || current == "null") return comp
        val parts = current.split(':').filter { it.isNotBlank() }
        return if (parts.contains(comp)) current else (parts + comp).joinToString(":")
    }
}
