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
 *  2. [addViaSecureSettings] — writes the tile spec straight into the
 *     `sysui_qs_tiles` secure setting, which is where the panel stores its tile
 *     list. Bypasses the edit UI entirely. Needs WRITE_SECURE_SETTINGS (adb or
 *     Shizuku), which SideKeys may already hold.
 *
 * Whether the tile actually appears is confirmed by [BatterySaverTile.onTileAdded],
 * not assumed.
 */
object QsTileInstaller {

    private const val QS_TILES = "sysui_qs_tiles"
    private const val SYSTEMUI = "com.android.systemui"

    fun component(context: Context) = ComponentName(context, BatterySaverTile::class.java)

    fun spec(context: Context): String = "custom(${component(context).flattenToString()})"

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

    /** Current tile list, or SystemUI's built-in default when the setting is unset. */
    fun currentTiles(context: Context): List<String> {
        val stored = Settings.Secure.getString(context.contentResolver, QS_TILES)
        val raw = if (stored.isNullOrBlank()) systemUiDefaultTiles(context) else stored
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun systemUiDefaultTiles(context: Context): String = runCatching {
        val res = context.packageManager.getResourcesForApplication(SYSTEMUI)
        val id = res.getIdentifier("quick_settings_tiles_default", "string", SYSTEMUI)
        if (id != 0) res.getString(id) else ""
    }.getOrDefault("")

    fun isListed(context: Context): Boolean = spec(context) in currentTiles(context)

    /**
     * Prepends our tile to the list (front, so it is visible even in a collapsed
     * panel that only shows the first row). Returns true if the write succeeded.
     */
    fun addViaSecureSettings(context: Context): Boolean {
        val tiles = currentTiles(context)
        val ours = spec(context)
        if (ours in tiles) return true
        return writeTiles(context, listOf(ours) + tiles)
    }

    fun removeViaSecureSettings(context: Context): Boolean {
        val tiles = currentTiles(context)
        val ours = spec(context)
        if (ours !in tiles) return true
        return writeTiles(context, tiles - ours)
    }

    private fun writeTiles(context: Context, tiles: List<String>): Boolean {
        val value = tiles.joinToString(",")
        if (PowerSaver.hasWriteSecureSettings(context)) {
            val ok = runCatching { Settings.Secure.putString(context.contentResolver, QS_TILES, value) }
                .getOrDefault(false)
            if (ok) return true
        }
        if (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()) {
            return ShizukuShell.run("settings put secure $QS_TILES '$value'").ok
        }
        return false
    }
}
