package com.sidekeys.hibreak.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Settings written by an older version must load unchanged. New fields fall
 * back to defaults, and those defaults must reproduce the old behaviour —
 * otherwise an update silently changes how the app feels for everyone.
 */
class SettingsCompatibilityTest {

    // Same configuration the repository uses to read stored settings.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `settings from before vibration strength keep the old pulse`() {
        val stored = """{"longPressMs":400,"doublePressMs":300,"hapticFeedback":true,"debounceMs":75}"""

        val settings = json.decodeFromString(KeySettings.serializer(), stored)

        assertEquals(VibrationStrength.LIGHT, settings.vibrationStrength)
        assertFalse("confirmation must stay opt-in", settings.confirmActions)
    }

    @Test
    fun `light is exactly the pulse every earlier version used`() {
        // 25 ms at the device's default amplitude. Changing this changes the
        // feel for every existing user who never opens the new setting.
        assertEquals(25L, VibrationStrength.LIGHT.durationMs)
        assertEquals(-1, VibrationStrength.LIGHT.amplitude)
    }

    @Test
    fun `stronger levels are longer, not only louder`() {
        // Many e-ink phones lack amplitude control; on them duration is the
        // only thing that makes a buzz feel stronger.
        val levels = VibrationStrength.entries.map { it.durationMs }
        assertEquals(levels.sorted(), levels)
        assertEquals(levels.size, levels.toSet().size)
    }
}
