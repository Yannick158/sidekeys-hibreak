package com.sidekeys.hibreak.core.model

import kotlinx.serialization.Serializable

/** All actions a side key press can trigger. */
@Serializable
enum class ActionType {
    NONE,

    /**
     * Swallow the key and do nothing with it.
     *
     * Distinct from [NONE]: a mapping whose slots are all NONE counts as empty
     * and the key is passed through untouched, which is what an unconfigured
     * key should do. BLOCK is the explicit "this key must stop here" — some
     * firmwares report their side keys as F1/F2, and apps then react to them.
     */
    BLOCK,

    /**
     * Hand the key to the foreground app untouched.
     *
     * Whole-key, not per-gesture: detecting a double or long press means
     * holding on to the DOWN event, so a key cannot be forwarded immediately
     * and gesture-detected at the same time. Any press type set to this makes
     * the whole key pass through.
     *
     * Needed because NONE in an app profile means "inherit the global
     * mapping" — without this there is no way to say "leave this key alone in
     * this app", which readers with their own page-turn keys need.
     */
    PASS_THROUGH,
    ASSISTANT,
    WALLET,
    LAUNCH_APP,
    LAUNCH_ACTIVITY,
    SCROLL_UP,
    SCROLL_DOWN,
    EINK_REFRESH,
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

/**
 * Full configuration of one physical key.
 *
 * [packageName] == null is the global mapping; otherwise it's an app-specific
 * profile that applies while that app is in the foreground. Slots left NONE in
 * a profile fall back to the global mapping.
 */
@Serializable
data class KeyMapping(
    val keyCode: Int,
    val keyName: String,
    val packageName: String? = null,
    val appLabel: String? = null,
    val singlePress: KeyAction = KeyAction(),
    val doublePress: KeyAction = KeyAction(),
    val longPress: KeyAction = KeyAction(),
    /**
     * Scroll distance for this profile, as a percentage of screen height.
     * null means "use the global setting" — a reading app may want a different
     * step than a browser.
     */
    val scrollPercent: Int? = null,
) {
    /** Merges an app profile over a global mapping slot by slot. */
    fun mergedOver(global: KeyMapping?): KeyMapping {
        if (global == null) return this
        return copy(
            singlePress = if (singlePress.type == ActionType.NONE) global.singlePress else singlePress,
            doublePress = if (doublePress.type == ActionType.NONE) global.doublePress else doublePress,
            longPress = if (longPress.type == ActionType.NONE) global.longPress else longPress,
            scrollPercent = scrollPercent ?: global.scrollPercent,
        )
    }

    /** True when the key must reach the foreground app untouched. */
    val isPassThrough: Boolean
        get() = singlePress.type == ActionType.PASS_THROUGH ||
            doublePress.type == ActionType.PASS_THROUGH ||
            longPress.type == ActionType.PASS_THROUGH

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
    /**
     * Keep SideKeys out of the recent-apps list. Bigme's task manager
     * force-stops apps that are swiped away / "closed", which kills the
     * accessibility service until it is toggled again. Hidden = can't be killed
     * that way.
     */
    val hideFromRecents: Boolean = true,
    /**
     * How far a scroll action moves, as a percentage of the screen height.
     * How much text that is depends on the font size, so it is worth adjusting.
     */
    val scrollPercent: Int = 45,
)
