package com.sidekeys.hibreak.core.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mapping list is persisted as JSON in DataStore, so old stored data must
 * keep decoding after the model gains fields (per-app profiles, charge alarm).
 */
class MappingSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(KeyMapping.serializer())

    @Test
    fun `mappings stored before per-app profiles still decode`() {
        val legacy = """
            [{"keyCode":100,"keyName":"F1 (100)",
              "singlePress":{"type":"HOME"},
              "doublePress":{"type":"NONE"},
              "longPress":{"type":"NONE"}}]
        """.trimIndent()

        val decoded = json.decodeFromString(listSerializer, legacy)

        assertEquals(1, decoded.size)
        assertEquals(100, decoded[0].keyCode)
        assertEquals(ActionType.HOME, decoded[0].singlePress.type)
        assertNull("legacy entries must become global mappings", decoded[0].packageName)
    }

    @Test
    fun `mapping survives an encode-decode round trip`() {
        val original = listOf(
            KeyMapping(
                keyCode = 92,
                keyName = "PAGE UP (92)",
                packageName = "com.example.reader",
                appLabel = "Reader",
                singlePress = KeyAction(ActionType.SCROLL_UP),
                longPress = KeyAction(ActionType.LAUNCH_ACTIVITY, data = "a/b.C", label = "C"),
            ),
        )

        val decoded = json.decodeFromString(listSerializer, json.encodeToString(listSerializer, original))

        assertEquals(original, decoded)
    }

    @Test
    fun `settings stored before the charge alarm still decode with defaults`() {
        val legacy = """{"longPressMs":500,"doublePressMs":250,"hapticFeedback":false}"""

        val decoded = json.decodeFromString(KeySettings.serializer(), legacy)

        assertEquals(500L, decoded.longPressMs)
        assertEquals(false, decoded.hapticFeedback)
        assertEquals("new field must use its default", 75L, decoded.debounceMs)
        assertEquals("new field must use its default", true, decoded.hideFromRecents)
    }
}
