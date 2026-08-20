package com.sidekeys.hibreak.core.common

import android.content.Context
import android.view.KeyEvent
import com.sidekeys.hibreak.R

object KeyCodeNames {

    /** Human-readable name for a key code, e.g. "F1 (131)" or a localized "Key 285". */
    fun prettyName(context: Context, keyCode: Int): String {
        val raw = KeyEvent.keyCodeToString(keyCode)
        val short = raw.removePrefix("KEYCODE_").replace('_', ' ')
        return if (short.isBlank() || short.startsWith("UNKNOWN") || short.toIntOrNull() != null) {
            context.getString(R.string.key_generic, keyCode)
        } else {
            "$short ($keyCode)"
        }
    }

    /**
     * Keys that must never be remapped: losing either one can leave the user
     * unable to reach the app that would undo the mapping.
     */
    val BLOCKED_KEY_CODES = setOf(
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_HOME,
    )

    /**
     * Navigation keys. Remapping them is allowed but confirmed first — several
     * e-ink readers report exactly these codes for their page-turn buttons, so
     * refusing them outright would make the app useless on those devices.
     */
    val RISKY_KEY_CODES = setOf(
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_MENU,
    )
}
