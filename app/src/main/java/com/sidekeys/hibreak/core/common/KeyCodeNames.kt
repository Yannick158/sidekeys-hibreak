package com.sidekeys.hibreak.core.common

import android.content.Context
import android.view.KeyEvent
import com.sidekeys.hibreak.R

object KeyCodeNames {

    /**
     * Stable identifier for a physical key.
     *
     * Some firmwares report their extra side keys as KEYCODE_UNKNOWN (0) — on a
     * Bigme B7 both side keys arrive as 0 and are indistinguishable by key code
     * alone. The kernel scan code still differs per button, so fall back to it,
     * negated so it can never collide with a real key code.
     */
    fun keyIdOf(event: KeyEvent): Int =
        if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN && event.scanCode != 0) {
            -event.scanCode
        } else {
            event.keyCode
        }

    /** Human-readable name for a key code, e.g. "F1 (131)" or a localized "Key 285". */
    fun prettyName(context: Context, keyCode: Int): String {
        // Negative ids are scan-code fallbacks; keyCodeToString knows nothing
        // about them, so name them after the scan code the hardware reports.
        if (keyCode < 0) return context.getString(R.string.key_scancode, -keyCode)
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
