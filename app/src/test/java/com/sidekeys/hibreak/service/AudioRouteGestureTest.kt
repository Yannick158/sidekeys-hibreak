package com.sidekeys.hibreak.service

import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.KeyAction
import com.sidekeys.hibreak.core.model.KeyMapping
import com.sidekeys.hibreak.core.model.KeySettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The audio route (volume keys via MediaSession) delivers bare ticks, so the
 * service synthesises gestures: DOWN on the first tick, and a synthetic UP
 * 130 ms after the last tick — further ticks only postpone it. These tests
 * drive [KeyPressHandler] with exactly that call pattern and pin down the
 * semantics the feature promises: single, double *and* long press, and a held
 * key firing exactly one gesture.
 */
class AudioRouteGestureTest {

    private companion object {
        const val SYNTH_UP_MS = 130L
        val AUDIO_SETTINGS = KeySettings(
            longPressMs = 400,
            doublePressMs = 300,
            debounceMs = 75, // user default; above the route's floor of 60
        )
    }

    private val scheduler = FakeScheduler()
    private val handler = KeyPressHandler(scheduler)
    private val fired = mutableListOf<ActionType>()
    private val execute: (KeyAction) -> Unit = { fired += it.type }

    /** One tick and silence: DOWN now, synthetic UP 130 ms later. */
    private fun tap(mapping: KeyMapping, at: Long) {
        require(scheduler.now <= at)
        scheduler.advanceBy(at - scheduler.now)
        handler.onDown(mapping, AUDIO_SETTINGS, 0, scheduler.now, execute)
        scheduler.advanceBy(SYNTH_UP_MS)
        handler.onUp(mapping, AUDIO_SETTINGS, scheduler.now, execute)
    }

    /** A held key: ticks every [intervalMs] keep postponing the synthetic UP. */
    private fun hold(mapping: KeyMapping, ticks: Int, intervalMs: Long) {
        handler.onDown(mapping, AUDIO_SETTINGS, 0, scheduler.now, execute)
        repeat(ticks - 1) { scheduler.advanceBy(intervalMs) }
        scheduler.advanceBy(SYNTH_UP_MS)
        handler.onUp(mapping, AUDIO_SETTINGS, scheduler.now, execute)
    }

    private fun mapping(
        single: ActionType = ActionType.VOLUME_UP, // the substituted fallback
        double: ActionType = ActionType.NONE,
        long: ActionType = ActionType.NONE,
    ) = KeyMapping(
        keyCode = 24,
        keyName = "Volume up",
        singlePress = KeyAction(single),
        doublePress = KeyAction(double),
        longPress = KeyAction(long),
    )

    @Test
    fun `a single tick fires the single action once`() {
        tap(mapping(single = ActionType.SCREENSHOT), at = 0)
        scheduler.advanceBy(1_000)
        assertEquals(listOf(ActionType.SCREENSHOT), fired)
    }

    @Test
    fun `single press keeps changing the volume when only double is assigned`() {
        // The service substitutes VOLUME_UP into an empty single slot; the
        // machine must fire it after the double-press window, not swallow it.
        tap(mapping(double = ActionType.HOME), at = 0)
        scheduler.advanceBy(1_000)
        assertEquals(listOf(ActionType.VOLUME_UP), fired)
    }

    @Test
    fun `two ticks within the window fire the double action only`() {
        val m = mapping(double = ActionType.HOME)
        tap(m, at = 0)
        tap(m, at = 250)
        scheduler.advanceBy(1_000)
        assertEquals(listOf(ActionType.HOME), fired)
    }

    @Test
    fun `a held key fires the long action mid-hold and nothing at release`() {
        // Auto-repeat ticks every 80 ms postpone the synthetic UP, so the DOWN
        // stays pressed and the ordinary long-press timer fires at 400 ms.
        hold(mapping(long = ActionType.FLASHLIGHT), ticks = 8, intervalMs = 80)
        scheduler.advanceBy(1_000)
        assertEquals(listOf(ActionType.FLASHLIGHT), fired)
    }

    @Test
    fun `a held key never counts as a double press`() {
        // Only double assigned: a hold must resolve to one single-slot action
        // (the volume fallback), not to HOME. This is the false-positive that
        // would make the feature unusable.
        hold(mapping(double = ActionType.HOME), ticks = 8, intervalMs = 80)
        scheduler.advanceBy(1_000)
        assertEquals(listOf(ActionType.VOLUME_UP), fired)
    }

    @Test
    fun `a second tap after the double window is two singles`() {
        val m = mapping(single = ActionType.SCREENSHOT, double = ActionType.HOME)
        tap(m, at = 0)
        // Window is 300 ms from the release at 130; tap at 600 is well past it.
        tap(m, at = 600)
        scheduler.advanceBy(1_000)
        assertEquals(listOf(ActionType.SCREENSHOT, ActionType.SCREENSHOT), fired)
    }
}
