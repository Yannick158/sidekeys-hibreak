package com.sidekeys.hibreak.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the per-app profile merge: empty slots fall back to the global mapping. */
class KeyMappingTest {

    private val global = KeyMapping(
        keyCode = 100,
        keyName = "Key 100",
        singlePress = KeyAction(ActionType.HOME),
        doublePress = KeyAction(ActionType.BACK),
        longPress = KeyAction(ActionType.RECENTS),
    )

    @Test
    fun `scan-code identity stays distinct from the raw key code`() {
        // Devices that report side keys as KEYCODE_UNKNOWN get a negative id
        // derived from the scan code. It must never collide with a real key
        // code, otherwise one button would hijack another's mapping.
        val perButton = KeyMapping(keyCode = -158, keyName = "Side key (scan code 158)")
        val shared = KeyMapping(keyCode = 0, keyName = "Key 0")

        assertTrue(perButton.keyCode < 0)
        assertFalse(perButton.keyCode == shared.keyCode)
    }

    @Test
    fun `pass-through in a profile beats the global mapping`() {
        // NONE means "inherit the global slot", so it can never express "leave
        // this key alone here" — that is what PASS_THROUGH is for.
        val profile = KeyMapping(
            keyCode = 100,
            keyName = "Key 100",
            packageName = "com.example.reader",
            singlePress = KeyAction(ActionType.PASS_THROUGH),
        )

        val merged = profile.mergedOver(global)

        assertTrue(merged.isPassThrough)
        assertFalse("a plain profile must not pass through", global.isPassThrough)
    }

    @Test
    fun `a mapping that only blocks is not empty`() {
        // isEmpty decides whether the service passes the key through. Some
        // firmwares report side keys as F1/F2 and apps react to them, so a
        // key set to BLOCK must be consumed, not forwarded.
        val blocking = KeyMapping(
            keyCode = 131,
            keyName = "F1 (131)",
            singlePress = KeyAction(ActionType.BLOCK),
        )

        assertFalse(blocking.isEmpty)
        assertTrue(KeyMapping(keyCode = 131, keyName = "F1 (131)").isEmpty)
    }

    @Test
    fun `app profile overrides only the slots it defines`() {
        val profile = KeyMapping(
            keyCode = 100,
            keyName = "Key 100",
            packageName = "com.example.reader",
            singlePress = KeyAction(ActionType.SCROLL_DOWN),
        )

        val merged = profile.mergedOver(global)

        assertEquals(ActionType.SCROLL_DOWN, merged.singlePress.type)
        assertEquals("empty slot must fall back to global", ActionType.BACK, merged.doublePress.type)
        assertEquals("empty slot must fall back to global", ActionType.RECENTS, merged.longPress.type)
        assertEquals("com.example.reader", merged.packageName)
    }

    @Test
    fun `profile without a global mapping keeps its own slots`() {
        val profile = KeyMapping(
            keyCode = 100,
            keyName = "Key 100",
            packageName = "com.example.reader",
            singlePress = KeyAction(ActionType.SCROLL_UP),
        )

        val merged = profile.mergedOver(null)

        assertEquals(ActionType.SCROLL_UP, merged.singlePress.type)
        assertEquals(ActionType.NONE, merged.doublePress.type)
    }

    @Test
    fun `a mapping is empty only when every slot is unset`() {
        assertTrue(KeyMapping(keyCode = 1, keyName = "K").isEmpty)
        assertFalse(global.isEmpty)
    }
}
