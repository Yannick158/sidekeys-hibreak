package com.sidekeys.hibreak.service

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.qs.BatterySaverTile
import java.util.concurrent.Executor

/**
 * Gets our Battery Saver tile into Quick Settings on firmwares whose panel
 * offers no "edit tiles" UI for third-party tiles (e.g. Bigme's).
 *
 * Two routes, tried in order:
 *  1. [requestViaSystemDialog] — the official Android 13+ API. Needs no
 *     permission; the system shows an "Add tile?" dialog. Only works if the
 *     firmware's SystemUI implements it.
 *  2. [addViaShell] — edits the `sysui_qs_tiles` secure setting, where the
 *     panel stores its tile list. Bypasses the edit UI entirely.
 *
 * Android 14 forbids apps targeting SDK 34 from *reading* `sysui_qs_tiles`
 * (SecurityException), so route 2 goes through a shell (Shizuku) which may.
 * Every call here is safe to invoke from anywhere — nothing throws — but the
 * shell calls block and must run off the main thread.
 *
 * Whether the tile actually appears is confirmed by [BatterySaverTile.onTileAdded],
 * not assumed.
 */
object QsTileInstaller {

    private const val QS_TILES = "sysui_qs_tiles"

    fun component(context: Context) = ComponentName(context, BatterySaverTile::class.java)

    fun spec(context: Context): String = "custom(${component(context).flattenToString()})"

    /** For users who granted rights via adb instead of Shizuku. */
    fun adbCommand(context: Context): String =
        "adb shell 'settings put secure $QS_TILES \"${spec(context)},\$(settings get secure $QS_TILES)\"'"

    /** Result of the system-dialog route. */
    enum class DialogResult { ADDED, ALREADY_ADDED, DECLINED, UNSUPPORTED }

    fun requestViaSystemDialog(context: Context, executor: Executor, onResult: (DialogResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(DialogResult.UNSUPPORTED)
            return
        }
        val sbm = context.getSystemService(StatusBarManager::class.java)
        if (sbm == null) {
            onResult(DialogResult.UNSUPPORTED)
            return
        }
        runCatching {
            sbm.requestAddTileService(
                component(context),
                context.getString(R.string.tile_battery_saver),
                Icon.createWithResource(context, R.drawable.ic_battery_saver),
                executor,
            ) { code ->
                onResult(
                    when (code) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> DialogResult.ADDED
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> DialogResult.ALREADY_ADDED
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> DialogResult.DECLINED
                        else -> DialogResult.UNSUPPORTED
                    },
                )
            }
        }.onFailure { onResult(DialogResult.UNSUPPORTED) }
    }

    private fun shellReady() = ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()

    /**
     * Current tile list, or null if it cannot be read (Android 14 restriction
     * and no shell available). Blocking when it has to go through the shell.
     */
    fun currentTiles(context: Context): List<String>? {
        // Direct read: works on Android 13 and below.
        val direct = runCatching { Settings.Secure.getString(context.contentResolver, QS_TILES) }
        if (direct.isSuccess) return parse(direct.getOrNull())
        // Android 14+: only a shell may read it.
        if (!shellReady()) return null
        val r = ShizukuShell.run("settings get secure $QS_TILES")
        if (!r.ok) return null
        return parse(r.stdout)
    }

    private fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw.trim() == "null") return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** True/false if known, null if the list cannot be read right now. Blocking. */
    fun isListed(context: Context): Boolean? = currentTiles(context)?.let { spec(context) in it }

    enum class ShellResult { OK, NO_SHELL, WRITE_FAILED }

    /**
     * Prepends our tile to the list (front, so it is visible even in a collapsed
     * panel that only shows the first row). Blocking.
     */
    fun addViaShell(context: Context): ShellResult {
        if (!shellReady()) return ShellResult.NO_SHELL
        val tiles = currentTiles(context) ?: return ShellResult.NO_SHELL
        val ours = spec(context)
        if (ours in tiles) return ShellResult.OK
        return write(listOf(ours) + tiles)
    }

    fun removeViaShell(context: Context): ShellResult {
        if (!shellReady()) return ShellResult.NO_SHELL
        val tiles = currentTiles(context) ?: return ShellResult.NO_SHELL
        val ours = spec(context)
        if (ours !in tiles) return ShellResult.OK
        return write(tiles - ours)
    }

    private fun write(tiles: List<String>): ShellResult {
        val value = tiles.joinToString(",")
        val ok = ShizukuShell.run("settings put secure $QS_TILES '$value'").ok
        return if (ok) ShellResult.OK else ShellResult.WRITE_FAILED
    }
}
