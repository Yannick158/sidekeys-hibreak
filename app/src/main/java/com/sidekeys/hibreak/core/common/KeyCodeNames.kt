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

    /** Keys that must never be captured/remapped so the user cannot lock themselves out. */
    val BLOCKED_KEY_CODES = setOf(
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_MENU,
    )
}
