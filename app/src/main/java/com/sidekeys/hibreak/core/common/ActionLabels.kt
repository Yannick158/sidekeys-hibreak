package com.sidekeys.hibreak.core.common

import android.content.Context
import androidx.annotation.StringRes
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.KeyAction

@StringRes
fun ActionType.labelRes(): Int = when (this) {
    ActionType.NONE -> R.string.action_none
    ActionType.ASSISTANT -> R.string.action_assistant
    ActionType.WALLET -> R.string.action_wallet
    ActionType.LAUNCH_APP -> R.string.action_launch_app
    ActionType.HOME -> R.string.action_home
    ActionType.BACK -> R.string.action_back
    ActionType.RECENTS -> R.string.action_recents
    ActionType.NOTIFICATIONS -> R.string.action_notifications
    ActionType.QUICK_SETTINGS -> R.string.action_quick_settings
    ActionType.POWER_DIALOG -> R.string.action_power_dialog
    ActionType.LOCK_SCREEN -> R.string.action_lock_screen
    ActionType.SCREENSHOT -> R.string.action_screenshot
    ActionType.FLASHLIGHT -> R.string.action_flashlight
    ActionType.MEDIA_PLAY_PAUSE -> R.string.action_media_play_pause
    ActionType.MEDIA_NEXT -> R.string.action_media_next
    ActionType.MEDIA_PREVIOUS -> R.string.action_media_previous
    ActionType.VOLUME_UP -> R.string.action_volume_up
    ActionType.VOLUME_DOWN -> R.string.action_volume_down
    ActionType.VOLUME_MUTE_TOGGLE -> R.string.action_volume_mute
    ActionType.DND_TOGGLE -> R.string.action_dnd
    ActionType.CUSTOM_INTENT -> R.string.action_custom_intent
}

/** User-facing label, e.g. "App starten: Spotify". */
fun KeyAction.displayLabel(context: Context): String {
    val base = context.getString(type.labelRes())
    return if (!label.isNullOrBlank() && type != ActionType.NONE) "$base: $label" else base
}
