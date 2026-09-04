package com.sidekeys.hibreak.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sidekeys.hibreak.R

/**
 * Fires the charge alarm: a heads-up notification whose channel carries the
 * sound and the vibration.
 *
 * Everything goes through the notification on purpose. Do Not Disturb governs
 * notifications; a direct `Vibrator.vibrate()` is not a notification at all,
 * and a directly played `Ringtone` carries an audio usage the default zen
 * policy lets through. An earlier version did both, and the alarm rang through
 * DND in the middle of the night while the channel's "override Do Not Disturb"
 * switch — the one control a user would look for — had no effect, because
 * nothing went through the channel.
 *
 * Now that switch is the real control: leave it off and DND silences the alarm;
 * turn it on and it breaks through by the user's own choice. The notification's
 * category matters just as much as the channel — see the builder below.
 */
object ChargeAlarm {

    /**
     * Bumped because a channel's vibration and audio attributes cannot be added
     * after creation, and the previous channel was created without them. The old
     * one is deleted so it stops accepting posts; system settings may still list
     * it among deleted categories for a while.
     */
    private const val CHANNEL_ID = "charge_alarm_v2"
    private const val LEGACY_CHANNEL_ID = "charge_alarm"
    private const val NOTIF_ID = 4711

    /**
     * Creates the channel ahead of the first alarm, so its "Override Do Not
     * Disturb" switch exists in system settings the moment the user turns the
     * alarm on — not only after it has fired once.
     */
    fun ensureChannel(context: Context) {
        ensureChannel(
            context,
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
        )
    }

    fun alert(context: Context, percent: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_saver)
            .setContentTitle(context.getString(R.string.charge_alarm_title))
            .setContentText(context.getString(R.string.charge_alarm_text, percent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Deliberately NOT CATEGORY_ALARM. ZenModeFiltering.isAlarm() matches
            // that category, and the default Do Not Disturb policy allows alarms
            // through — the channel's bypass switch is never even consulted. It
            // would have preserved the exact bug this fix is for. CATEGORY_STATUS
            // matches none of the zen exceptions.
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        // Without POST_NOTIFICATIONS (Android 13+) there is no way to alert at
        // all any more — deliberately, since anything else would sidestep DND.
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
        }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        // Carry over a lowered importance from the old channel, so a user who
        // had quietened or blocked the alarm does not get it back at full
        // volume just because the channel id changed.
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        val legacy = manager.getNotificationChannel(LEGACY_CHANNEL_ID)
        val importance = existing?.importance
            ?: legacy?.importance?.takeIf { it < NotificationManager.IMPORTANCE_HIGH }
            ?: NotificationManager.IMPORTANCE_HIGH
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }

        // Recreated on every call, not skipped when it exists: the framework
        // ignores the immutable fields for an existing channel but does apply
        // name and description, which must follow the app's language.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.charge_alarm_channel),
            importance,
        ).apply {
            description = context.getString(R.string.charge_alarm_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            // Off by default: the user decides in the system's channel settings
            // whether this may interrupt Do Not Disturb.
            setBypassDnd(false)
        }
        manager.createNotificationChannel(channel)
    }
}
