package com.sidekeys.hibreak.service

import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.KeyAction
import com.sidekeys.hibreak.core.model.KeyMapping
import com.sidekeys.hibreak.core.model.KeySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the single/double/long-press state machine and the two-sided debounce
 * that the bouncing HiBreak Pro side keys need.
 */
class KeyPressHandlerTest {

    private lateinit var scheduler: FakeScheduler
    private lateinit var handler: KeyPressHandler
    private val fired = mutableListOf<ActionType>()
    private val settings = KeySettings(longPressMs = 400, doublePressMs = 300, debounceMs = 75)

    @Before
    fun setUp() {
        scheduler = FakeScheduler()
        handler = KeyPressHandler(scheduler)
        fired.clear()
    }

    private fun mapping(
        single: ActionType = ActionType.NONE,
        double: ActionType = ActionType.NONE,
        long: ActionType = ActionType.NONE,
    ) = KeyMapping(
        keyCode = 100,
        keyName = "Key 100",
        singlePress = KeyAction(single),
        doublePress = KeyAction(double),
        longPress = KeyAction(long),
    )

    private fun down(m: KeyMapping) = handler.onDown(m, settings, 0, scheduler.now) { action, _ -> fired += action.type }

    private fun up(m: KeyMapping) = handler.onUp(m, settings, scheduler.now) { action, _ -> fired += action.type }

    @Test
    fun `single press fires on release when no double press is configured`() {
        val m = mapping(single = ActionType.HOME)
        down(m)
        scheduler.advanceBy(100)
        up(m)

        assertEquals(listOf(ActionType.HOME), fired)
    }

    @Test
    fun `single press waits for the double press window when a double press exists`() {
        val m = mapping(single = ActionType.HOME, double = ActionType.BACK)
        down(m)
        scheduler.advanceBy(100)
        up(m)

        assertTrue("single must not fire before the window elapses", fired.isEmpty())

        scheduler.advanceBy(300)
        assertEquals(listOf(ActionType.HOME), fired)
    }

    @Test
    fun `second tap inside the window fires the double press and cancels the single`() {
        val m = mapping(single = ActionType.HOME, double = ActionType.BACK)
        down(m)
        scheduler.advanceBy(100)
        up(m)
        scheduler.advanceBy(100)
        down(m)
        scheduler.advanceBy(100)
        up(m)

        assertEquals(listOf(ActionType.BACK), fired)

        scheduler.advanceBy(1000)
        assertEquals("the pending single press must not fire later", listOf(ActionType.BACK), fired)
    }

    @Test
    fun `long press fires while held and suppresses the single press`() {
        val m = mapping(single = ActionType.HOME, long = ActionType.RECENTS)
        down(m)
        scheduler.advanceBy(400)

        assertEquals(listOf(ActionType.RECENTS), fired)

        up(m)
        scheduler.advanceBy(1000)
        assertEquals("release after a long press must not add a single press", listOf(ActionType.RECENTS), fired)
    }

    @Test
    fun `break bounce - a press right after a release is swallowed`() {
        val m = mapping(single = ActionType.HOME)
        down(m)
        scheduler.advanceBy(100)
        up(m)
        assertEquals(listOf(ActionType.HOME), fired)

        // Ghost press 20 ms after the release (below the 75 ms debounce).
        scheduler.advanceBy(20)
        down(m)
        scheduler.advanceBy(10)
        up(m)
        scheduler.advanceBy(1000)

        assertEquals("bounced press must not fire a second action", listOf(ActionType.HOME), fired)
    }

    @Test
    fun `make bounce - a spurious release keeps the long press armed`() {
        val m = mapping(single = ActionType.HOME, long = ActionType.RECENTS)
        down(m)
        // Contact bounce: release after 20 ms, pressed again 20 ms later.
        scheduler.advanceBy(20)
        up(m)
        scheduler.advanceBy(20)
        down(m)

        // Key is still physically held -> the long press must still fire.
        scheduler.advanceBy(400)
        assertEquals(listOf(ActionType.RECENTS), fired)
    }

    @Test
    fun `a release without a matching press is passed through to the system`() {
        val m = mapping(single = ActionType.HOME)

        assertFalse("orphaned UP must not be consumed", up(m))
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `reset cancels every pending action`() {
        val m = mapping(single = ActionType.HOME, double = ActionType.BACK, long = ActionType.RECENTS)
        down(m)
        assertTrue(handler.hasActiveGesture())

        handler.reset()
        scheduler.advanceBy(5000)

        assertTrue("no action may fire after reset", fired.isEmpty())
        assertEquals(0, scheduler.pendingCount)
        assertFalse(handler.hasActiveGesture())
    }

    // --- Repeat while held (volume only) ---

    @Test
    fun `a held volume key keeps stepping until it is released`() {
        val m = mapping(long = ActionType.VOLUME_DOWN)
        down(m)
        scheduler.advanceBy(400) // long press fires: one step
        assertEquals(listOf(ActionType.VOLUME_DOWN), fired)

        scheduler.advanceBy(360) // three more at 120 ms
        assertEquals(4, fired.size)
        assertTrue(fired.all { it == ActionType.VOLUME_DOWN })

        up(m)
        scheduler.advanceBy(1_000)
        assertEquals("release must stop the repeat", 4, fired.size)
    }

    @Test
    fun `repeats are marked as repeats, so only the first step buzzes`() {
        val m = mapping(long = ActionType.VOLUME_UP)
        val repeatFlags = mutableListOf<Boolean>()
        handler.onDown(m, settings, 0, scheduler.now) { _, repeated -> repeatFlags += repeated }
        scheduler.advanceBy(400 + 240)
        handler.onUp(m, settings, scheduler.now) { _, repeated -> repeatFlags += repeated }

        assertEquals(listOf(false, true, true), repeatFlags)
    }

    @Test
    fun `actions other than volume still fire once per hold`() {
        val m = mapping(long = ActionType.DND_TOGGLE)
        down(m)
        scheduler.advanceBy(2_000)
        assertEquals(listOf(ActionType.DND_TOGGLE), fired)
        up(m)
    }

    @Test
    fun `reset stops a running repeat`() {
        val m = mapping(long = ActionType.VOLUME_DOWN)
        down(m)
        scheduler.advanceBy(400)
        handler.reset()
        scheduler.advanceBy(1_000)
        assertEquals(1, fired.size)
        assertFalse(handler.hasActiveGesture())
    }
}
