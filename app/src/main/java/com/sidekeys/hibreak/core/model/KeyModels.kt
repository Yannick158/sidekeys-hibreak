package com.sidekeys.hibreak.core.model

import kotlinx.serialization.Serializable

/** All actions a side key press can trigger. */
@Serializable
enum class ActionType {
    NONE,
    ASSISTANT,
    WALLET,
    LAUNCH_APP,
    HOME,
    BACK,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    POWER_DIALOG,
    LOCK_SCREEN,
    SCREENSHOT,
    FLASHLIGHT,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE_TOGGLE,
    DND_TOGGLE,
    BATTERY_SAVER_TOGGLE,
    CUSTOM_INTENT,
}

/**
 * A single configured action.
 *
 * [data] carries the action payload: the package name for [ActionType.LAUNCH_APP],
 * or a serialized [CustomIntentSpec] for [ActionType.CUSTOM_INTENT].
 * [label] is a user-visible name (e.g. the picked app's name).
 */
@Serializable
data class KeyAction(
    val type: ActionType = ActionType.NONE,
    val data: String? = null,
    val label: String? = null,
)

/** How a custom intent should be dispatched. */
@Serializable
enum class CustomIntentMode { ACTIVITY, BROADCAST }

/** User-defined intent, e.g. for Bigme-specific settings actions. */
@Serializable
data class CustomIntentSpec(
    val mode: CustomIntentMode = CustomIntentMode.ACTIVITY,
    val action: String = "",
    val component: String = "",
    val dataUri: String = "",
)

/** Which press gesture triggered an action. */
enum class PressType { SINGLE, DOUBLE, LONG }

/** Full configuration of one physical key. */
@Serializable
data class KeyMapping(
    val keyCode: Int,
    val keyName: String,
    val singlePress: KeyAction = KeyAction(),
    val doublePress: KeyAction = KeyAction(),
    val longPress: KeyAction = KeyAction(),
) {
    val isEmpty: Boolean
        get() = singlePress.type == ActionType.NONE &&
            doublePress.type == ActionType.NONE &&
            longPress.type == ActionType.NONE

    fun action(pressType: PressType): KeyAction = when (pressType) {
        PressType.SINGLE -> singlePress
        PressType.DOUBLE -> doublePress
        PressType.LONG -> longPress
    }
}

/**
 * Charge alarm: alert (sound/vibration/notification) at [alarmPercent] while
 * plugged in, so the user can unplug. Works on any device — no root or writable
 * charging node needed.
 */
@Serializable
data class ChargeSettings(
    val alarmEnabled: Boolean = false,
    val alarmPercent: Int = 80,
)

/** Global behaviour settings. */
@Serializable
data class KeySettings(
    val longPressMs: Long = 400,
    val doublePressMs: Long = 300,
    val hapticFeedback: Boolean = true,
    /**
     * The HiBreak Pro side keys are known to bounce and fire spurious double
     * presses; presses arriving faster than this are ignored.
     */
    val debounceMs: Long = 75,
)
