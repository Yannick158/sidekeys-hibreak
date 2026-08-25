package com.sidekeys.hibreak.service

import android.content.Context
import android.provider.Settings

/**
 * Keeps MediaTek's DuraSpeed switched off.
 *
 * DuraSpeed aggressively stops background apps — including this one's
 * accessibility service, which then silently stops remapping keys until it is
 * toggled again. Disabling the `com.mediatek.duraspeed` package is a red
 * herring: it only removes the notification, not the killing.
 *
 * Two quirks reported on affected devices ([dontkillmyapp.com](https://dontkillmyapp.com/nokia)),
 * both handled by [disable]:
 *  - the value has to be written to Global *and* System, not just one,
 *  - writing 0 directly can be ignored, so it is set non-zero first.
 *
 * Whether the value then survives a reboot varies by vendor, which is why
 * [KeyInterceptorService] watches it and re-applies rather than writing once.
 */
object DuraSpeed {

    private const val KEY = "setting.duraspeed.enabled"

    /**
     * Guards against the observer chasing our own writes. [disable] deliberately
     * sets the value to 1 before 0, and the watcher sees that 1, concludes
     * DuraSpeed is on and calls back in — so without this the two feed each
     * other.
     */
    private val applying = java.util.concurrent.atomic.AtomicBoolean(false)

    /** True while [disable] is mid-sequence; the watcher skips those changes. */
    fun isApplying(): Boolean = applying.get()

    /** Uri to observe. Global is the namespace every affected device exposes. */
    fun globalUri() = Settings.Global.getUriFor(KEY)

    /**
     * Null when the device has no DuraSpeed at all — most phones. Callers use
     * this to hide the feature rather than offer a switch that does nothing.
     */
    fun currentValue(context: Context): Int? {
        runCatching {
            val raw = Settings.Global.getString(context.contentResolver, KEY)
            if (raw != null) return raw.trim().toIntOrNull() ?: 0
        }
        // Reading Global can throw on some builds; the shell always sees it.
        if (shizukuReady()) {
            val result = ShizukuShell.run("settings get global $KEY")
            val raw = result.stdout.trim()
            if (result.ok && raw.isNotEmpty() && raw != "null") return raw.toIntOrNull() ?: 0
        }
        return null
    }

    fun isPresent(context: Context): Boolean = currentValue(context) != null

    fun isDisabled(context: Context): Boolean = currentValue(context) == 0

    fun canWrite(context: Context): Boolean =
        PowerSaver.hasWriteSecureSettings(context) || shizukuReady()

    /**
     * Applies the off state. Blocks on the shell path, so never call from the
     * main thread. Returns true if the value reads back as 0 afterwards.
     */
    fun disable(context: Context): Boolean {
        if (!applying.compareAndSet(false, true)) return isDisabled(context)
        return try {
            // Non-zero first: writing 0 over an existing 0-but-inactive state is
            // reported to be a no-op on some firmwares.
            writeBoth(context, 1)
            writeBoth(context, 0)
            isDisabled(context)
        } finally {
            applying.set(false)
        }
    }

    private fun writeBoth(context: Context, value: Int) {
        if (PowerSaver.hasWriteSecureSettings(context)) {
            runCatching { Settings.Global.putInt(context.contentResolver, KEY, value) }
        }
        if (shizukuReady()) {
            // System needs a shell: WRITE_SECURE_SETTINGS does not cover that
            // namespace, and asking for WRITE_SETTINGS just for this is not
            // worth the extra permission on the listing.
            ShizukuShell.run("settings put global $KEY $value; settings put system $KEY $value")
        }
    }

    private fun shizukuReady(): Boolean =
        ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()
}
