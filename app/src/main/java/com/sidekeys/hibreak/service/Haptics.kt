package com.sidekeys.hibreak.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sidekeys.hibreak.core.model.VibrationStrength

/**
 * The key-press buzz, shared by the service and the settings preview so the
 * preview feels exactly like the real thing.
 */
object Haptics {

    fun buzz(context: Context, strength: VibrationStrength) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // Without amplitude control an explicit amplitude is ignored at best;
            // the device default keeps the behaviour predictable.
            val amplitude = if (vibrator.hasAmplitudeControl() && strength.amplitude > 0) {
                strength.amplitude
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            vibrator.vibrate(VibrationEffect.createOneShot(strength.durationMs, amplitude))
        }
    }
}
