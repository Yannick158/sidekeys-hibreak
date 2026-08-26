package com.sidekeys.hibreak.service

import android.content.Context
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps MediaTek's DuraSpeed switched off.
 *
 * DuraSpeed stops background apps — this service among them, which then quietly
 * stops remapping keys until it is switched on again. Disabling the
 * `com.mediatek.duraspeed` package is a red herring: it removes the
 * notification, not the killing.
 *
 * The behaviour is genuinely awkward, and the details below come from a user's
 * testing on Bigme hardware (https://www.reddit.com/r/Bigme/s/M8tCfoal21):
 *
 *  - **Global** takes effect immediately but is not read back at boot. The value
 *    survives a reboot while DuraSpeed comes back up active anyway, so a value
 *    of 0 there proves nothing.
 *  - **System** is the one that survives properly, but only from the next boot.
 *  - Writing 0 over an existing 0 does nothing, so the value is nudged through
 *    another value first — **not 1**, because some firmwares use 1 as their
 *    default "on" value and the transition may not register as a change.
 *  - `dumpsys duraspeed status` reports what the service actually thinks it is,
 *    which is worth more than either stored value.
 */
object DuraSpeed {

    private const val KEY = "setting.duraspeed.enabled"

    /**
     * Nudge value written before 0.
     *
     * 2, because that is what dontkillmyapp.com documents and what Nokia users
     * have had working for years. Explicitly **not 1**: some firmwares use 1 as
     * their default "enabled" value, so 1 -> 0 may not register as a change.
     */
    private const val PRE_ZERO = 2

    private val applying = AtomicBoolean(false)

    /** True while [disable] is mid-sequence; the watcher skips its own writes. */
    fun isApplying(): Boolean = applying.get()

    fun globalUri() = Settings.Global.getUriFor(KEY)

    private fun shizukuReady(): Boolean =
        ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()

    /**
     * What the DuraSpeed service reports about itself, or null when it cannot be
     * asked (no shell, or no such service). More reliable than the stored value,
     * which can read 0 while DuraSpeed is running regardless.
     */
    fun serviceActive(): Boolean? {
        if (!shizukuReady()) return null
        val result = ShizukuShell.run("dumpsys duraspeed status")
        if (!result.ok) return null
        return Regex("\\b(true|false)\\b").find(result.stdout.lowercase())
            ?.value?.toBooleanStrictOrNull()
    }

    /** The stored Global value; null when the device has no DuraSpeed at all. */
    fun currentValue(context: Context): Int? {
        runCatching {
            val raw = Settings.Global.getString(context.contentResolver, KEY)
            if (raw != null) return raw.trim().toIntOrNull() ?: 0
        }
        if (shizukuReady()) {
            val result = ShizukuShell.run("settings get global $KEY")
            val raw = result.stdout.trim()
            if (result.ok && raw.isNotEmpty() && raw != "null") return raw.toIntOrNull() ?: 0
        }
        return null
    }

    fun isPresent(context: Context): Boolean =
        currentValue(context) != null || serviceActive() != null

    /**
     * Whether DuraSpeed is running, or null when that cannot be established.
     *
     * Only [serviceActive] answers this honestly. The stored value does not:
     * on Bigme hardware the Global value reads 0 while DuraSpeed is up and
     * running, because that firmware reads System at startup instead. Reporting
     * "off" from the value alone would tell the user the opposite of the truth,
     * so without a shell to ask with, this says it does not know.
     */
    fun isActive(context: Context): Boolean? = serviceActive()

    /** Worth writing again whenever we cannot prove it is already off. */
    fun needsDisabling(context: Context): Boolean = serviceActive() != false

    /**
     * Whether the durable fix is possible. It writes Settings.System, reachable
     * either with the user-granted WRITE_SETTINGS permission or through a shell.
     */
    fun canPersist(context: Context): Boolean = shizukuReady()

    fun canWrite(context: Context): Boolean =
        PowerSaver.hasWriteSecureSettings(context) || shizukuReady()

    /**
     * Switches DuraSpeed off in both namespaces. Blocks on the shell path, so
     * never call it from the main thread.
     *
     * Returns true only when the service confirms it is off. The System write
     * takes effect from the next boot, so false does not mean the write failed —
     * it usually means a restart is still pending.
     */
    fun disable(context: Context): Boolean {
        if (!applying.compareAndSet(false, true)) return false
        return try {
            // Global: immediate, and the only namespace reachable without a shell.
            writeGlobal(context, PRE_ZERO)
            writeGlobal(context, 0)
            // System: what DuraSpeed reads at boot, and the only namespace that
            // makes the change survive a restart.
            writeSystem(context, PRE_ZERO)
            writeSystem(context, 0)
            serviceActive() == false
        } finally {
            applying.set(false)
        }
    }

    private fun writeSystem(context: Context, value: Int) {
        if (shizukuReady()) ShizukuShell.run("settings put system $KEY $value")
    }

    private fun writeGlobal(context: Context, value: Int) {
        if (PowerSaver.hasWriteSecureSettings(context)) {
            val ok = runCatching {
                Settings.Global.putInt(context.contentResolver, KEY, value)
            }.isSuccess
            if (ok) return
        }
        if (shizukuReady()) ShizukuShell.run("settings put global $KEY $value")
    }
}
