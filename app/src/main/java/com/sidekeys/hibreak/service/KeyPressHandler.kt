package com.sidekeys.hibreak.service

import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.KeyAction
import com.sidekeys.hibreak.core.model.KeyMapping
import com.sidekeys.hibreak.core.model.KeySettings

/**
 * Per-key state machine that turns raw DOWN/UP events into
 * single-press, double-press and long-press gestures.
 *
 * Timing rules:
 *  - Holding longer than [KeySettings.longPressMs] fires the long-press action.
 *  - If a double-press action is configured, a single press only fires after
 *    [KeySettings.doublePressMs] without a second tap. Without a double-press
 *    action, the single press fires immediately on key-up.
 *
 * Debounce (the HiBreak Pro side keys bounce on both edges):
 *  - Break bounce (spurious DOWN shortly after a release) is swallowed outright.
 *  - Make bounce (spurious UP shortly after a press) is handled by delaying the
 *    release processing by [KeySettings.debounceMs]; if a DOWN arrives within
 *    that window the release was a bounce and the gesture (incl. a pending
 *    long press) simply continues. Real ultra-short taps are only delayed by
 *    debounceMs, never lost.
 *
 * All calls must happen on one thread (the main thread in production).
 */
class KeyPressHandler(private val scheduler: Scheduler) {

    private var longPressFired = false
    private var awaitingSecondTap = false
    private var isSecondTap = false
    private var isPressed = false
    private var pendingSingle: Runnable? = null
    private var pendingLong: Runnable? = null
    private var pendingRelease: Runnable? = null
    private var lastUpTime = 0L
    private var lastDownTime = 0L
    private var bouncing = false

    fun onDown(
        mapping: KeyMapping,
        settings: KeySettings,
        repeatCount: Int,
        eventTime: Long,
        execute: (KeyAction) -> Unit,
    ): Boolean {
        if (repeatCount > 0) return true

        // A release is still awaiting confirmation -> the previous UP was a
        // make-bounce. Cancel the pending release and let the gesture continue
        // (a scheduled long press stays armed).
        pendingRelease?.let {
            scheduler.cancel(it)
            pendingRelease = null
            return true
        }

        // Break bounce: swallow presses arriving too fast after a release.
        // Only meaningful once a release was actually seen (lastUpTime > 0),
        // otherwise the very first press would be mistaken for a bounce.
        if (settings.debounceMs > 0 && lastUpTime > 0L && eventTime - lastUpTime < settings.debounceMs) {
            bouncing = true
            return true
        }
        bouncing = false
        isPressed = true
        lastDownTime = eventTime
        longPressFired = false

        if (awaitingSecondTap) {
            pendingSingle?.let(scheduler::cancel)
            pendingSingle = null
            awaitingSecondTap = false
            isSecondTap = true
        }

        if (mapping.longPress.type != ActionType.NONE) {
            val runnable = Runnable {
                longPressFired = true
                pendingLong = null
                execute(mapping.longPress)
            }
            pendingLong = runnable
            scheduler.postDelayed(runnable, settings.longPressMs)
        }
        return true
    }

    fun onUp(
        mapping: KeyMapping,
        settings: KeySettings,
        eventTime: Long,
        execute: (KeyAction) -> Unit,
    ): Boolean {
        if (bouncing) {
            bouncing = false
            lastUpTime = eventTime
            return true
        }

        // No matching DOWN was processed by us (e.g. it happened before the
        // mapping was loaded, or during capture) -> pass the UP through, the
        // system saw the DOWN too.
        if (!isPressed) return false

        // UP arriving very shortly after the DOWN may be a make-bounce.
        // Delay the release processing; a bounce-DOWN within the window
        // cancels it and the gesture continues.
        if (settings.debounceMs > 0 && eventTime - lastDownTime < settings.debounceMs) {
            lastUpTime = eventTime
            val runnable = Runnable {
                pendingRelease = null
                commitRelease(mapping, settings, execute)
            }
            pendingRelease = runnable
            scheduler.postDelayed(runnable, settings.debounceMs)
            return true
        }

        lastUpTime = eventTime
        commitRelease(mapping, settings, execute)
        return true
    }

    private fun commitRelease(
        mapping: KeyMapping,
        settings: KeySettings,
        execute: (KeyAction) -> Unit,
    ) {
        isPressed = false
        pendingLong?.let(scheduler::cancel)
        pendingLong = null

        if (longPressFired) {
            longPressFired = false
            isSecondTap = false
            return
        }

        if (isSecondTap) {
            isSecondTap = false
            if (mapping.doublePress.type != ActionType.NONE) execute(mapping.doublePress)
            return
        }

        if (mapping.doublePress.type != ActionType.NONE) {
            awaitingSecondTap = true
            val runnable = Runnable {
                awaitingSecondTap = false
                pendingSingle = null
                if (mapping.singlePress.type != ActionType.NONE) execute(mapping.singlePress)
            }
            pendingSingle = runnable
            scheduler.postDelayed(runnable, settings.doublePressMs)
        } else if (mapping.singlePress.type != ActionType.NONE) {
            execute(mapping.singlePress)
        }
    }

    /** True while a gesture is in flight (key held, timers pending). */
    fun hasActiveGesture(): Boolean =
        isPressed || awaitingSecondTap || isSecondTap ||
            pendingSingle != null || pendingLong != null || pendingRelease != null

    fun reset() {
        pendingSingle?.let(scheduler::cancel)
        pendingLong?.let(scheduler::cancel)
        pendingRelease?.let(scheduler::cancel)
        pendingSingle = null
        pendingLong = null
        pendingRelease = null
        longPressFired = false
        awaitingSecondTap = false
        isSecondTap = false
        isPressed = false
        bouncing = false
        lastUpTime = 0L
        lastDownTime = 0L
    }
}
