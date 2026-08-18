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
