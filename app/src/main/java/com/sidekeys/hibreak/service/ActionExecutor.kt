package com.sidekeys.hibreak.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.CustomIntentMode
import com.sidekeys.hibreak.core.model.CustomIntentSpec
import com.sidekeys.hibreak.core.model.KeyAction
import kotlinx.serialization.json.Json

/**
 * Executes a configured [KeyAction]. Runs inside the accessibility service so
 * global actions (Home, Recents, Screenshot, ...) are available.
 */
class ActionExecutor(private val service: AccessibilityService) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cameraManager = service.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchCameraId: String? = null
    private var torchEnabled = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchEnabled = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) torchEnabled = false
        }
    }

    init {
        torchCameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        runCatching { cameraManager.registerTorchCallback(torchCallback, null) }
    }

    fun release() {
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
    }

    fun execute(action: KeyAction) {
        when (action.type) {
            ActionType.NONE -> Unit
            ActionType.ASSISTANT -> launchAssistant()
            ActionType.WALLET -> launchWallet()
            ActionType.LAUNCH_APP -> launchApp(action.data)
            ActionType.HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ActionType.BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionType.RECENTS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            ActionType.NOTIFICATIONS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            ActionType.QUICK_SETTINGS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            ActionType.POWER_DIALOG -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            ActionType.LOCK_SCREEN -> lockScreen()
            ActionType.SCREENSHOT -> takeScreenshot()
            ActionType.FLASHLIGHT -> toggleFlashlight()
            ActionType.MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionType.MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            ActionType.MEDIA_PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ActionType.DND_TOGGLE -> toggleDoNotDisturb()
            ActionType.CUSTOM_INTENT -> sendCustomIntent(action.data)
        }
    }

    fun vibrate() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                service.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun launchAssistant() {
        // ACTION_VOICE_COMMAND is the most reliable path (used by Key Mapper);
        // pinning the Google app first avoids a disambiguation chooser.
        val candidates = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GOOGLE_APP_PACKAGE),
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent(Intent.ACTION_ASSIST),
            service.packageManager.getLaunchIntentForPackage(GOOGLE_APP_PACKAGE),
        )
        for (intent in candidates.filterNotNull()) {
            if (startActivitySafely(intent)) return
        }
        toast(R.string.error_assistant_not_available)
    }

    private fun launchWallet() {
        // Card carousel via the QuickAccessWallet action (how SystemUI opens it),
        // guarded by resolveActivity since Wallet may not export it on all versions.
        val viewWallet = Intent(ACTION_VIEW_WALLET).setPackage(WALLET_PACKAGE)
        if (service.packageManager.resolveActivity(viewWallet, 0) != null &&
            startActivitySafely(viewWallet)
        ) {
            return
        }
        service.packageManager.getLaunchIntentForPackage(WALLET_PACKAGE)?.let { intent ->
            if (startActivitySafely(intent)) return
        }
        // Fall back to the app-link, then to the Play Store entry.
        if (startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://wallet.google.com/gw/app")))) return
        toast(R.string.error_wallet_not_installed)
        startActivitySafely(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$WALLET_PACKAGE")),
        )
    }

    private fun launchApp(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            toast(R.string.error_app_not_found)
            return
        }
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null || !startActivitySafely(intent)) {
            toast(R.string.error_app_not_found)
        }
    }

    private fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            toast(R.string.error_requires_android_9)
        }
    }

    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            toast(R.string.error_requires_android_9)
        }
    }

    private fun toggleFlashlight() {
        val cameraId = torchCameraId
        if (cameraId == null) {
            toast(R.string.error_no_flashlight)
            return
        }
        // Optimistic update: the TorchCallback arrives asynchronously, so two
        // quick toggles would otherwise both read the same stale state.
        val newState = !torchEnabled
        runCatching {
            cameraManager.setTorchMode(cameraId, newState)
            torchEnabled = newState
        }.onFailure {
            toast(R.string.error_action_failed)
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun toggleDoNotDisturb() {
        val notificationManager =
            service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            toast(R.string.error_dnd_permission)
            startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        runCatching {
            val current = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(
                if (current == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                },
            )
        }.onFailure { toast(R.string.error_action_failed) }
    }

    private fun sendCustomIntent(rawSpec: String?) {
        if (rawSpec.isNullOrBlank()) {
            toast(R.string.error_action_failed)
            return
        }
        val spec = runCatching { json.decodeFromString(CustomIntentSpec.serializer(), rawSpec) }.getOrNull()
        if (spec == null || (spec.action.isBlank() && spec.component.isBlank())) {
            toast(R.string.error_action_failed)
            return
        }
        val intent = Intent().apply {
            if (spec.action.isNotBlank()) action = spec.action
            if (spec.component.isNotBlank()) {
                ComponentName.unflattenFromString(spec.component)?.let { component = it }
            }
            if (spec.dataUri.isNotBlank()) data = Uri.parse(spec.dataUri)
        }
        val result = runCatching {
            when (spec.mode) {
                CustomIntentMode.ACTIVITY -> {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(intent)
                }
                CustomIntentMode.BROADCAST -> service.sendBroadcast(intent)
            }
        }
        if (result.isFailure) toast(R.string.error_action_failed)
    }

    private fun startActivitySafely(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: Exception) {
        false
    }

    private fun toast(resId: Int) {
        Toast.makeText(service, resId, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** Google Wallet — correct package for Germany/Europe. */
        private const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
        private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

        /** QuickAccessWalletService.ACTION_VIEW_WALLET (API 30 framework constant). */
        private const val ACTION_VIEW_WALLET = "android.service.quickaccesswallet.action.VIEW_WALLET"
    }
}
