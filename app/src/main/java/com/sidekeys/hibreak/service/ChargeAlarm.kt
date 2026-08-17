package com.sidekeys.hibreak.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sidekeys.hibreak.R

/**
 * Fires the charge alarm: a short vibration, a notification sound and a
 * heads-up notification telling the user to unplug. All best-effort — none of
 * the pieces require Shizuku or root.
 */
object ChargeAlarm {

    private const val CHANNEL_ID = "charge_alarm"
    private const val NOTIF_ID = 4711

    fun alert(context: Context, percent: Int) {
        vibrate(context)
        playSound(context)
        notify(context, percent)
    }

    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1),
            )
        }
    }

    private fun playSound(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }

    private fun notify(context: Context, percent: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.charge_alarm_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_saver)
            .setContentTitle(context.getString(R.string.charge_alarm_title))
            .setContentText(context.getString(R.string.charge_alarm_text, percent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        // POST_NOTIFICATIONS may be missing (Android 13+); the sound/vibration
        // still fired, so just skip the notification then.
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
        }
    }
}
