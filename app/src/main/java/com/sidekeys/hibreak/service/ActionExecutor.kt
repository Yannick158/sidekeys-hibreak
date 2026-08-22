package com.sidekeys.hibreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.graphics.Path
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraManager = service.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchCameraId: String? = null

    /** Scroll distance as a percentage of screen height; kept in sync by the service. */
    var scrollPercent: Int = 45
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

    fun execute(action: KeyAction, scrollPercentOverride: Int? = null) {
        val scrollBy = scrollPercentOverride ?: scrollPercent
        when (action.type) {
            ActionType.NONE -> Unit
            // The point is that nothing happens — the key is already consumed.
            ActionType.BLOCK -> Unit
            // Handled before dispatch: the key was never consumed.
            ActionType.PASS_THROUGH -> Unit
            ActionType.ASSISTANT -> launchAssistant()
            ActionType.WALLET -> launchWallet()
            ActionType.LAUNCH_APP -> launchApp(action.data)
            ActionType.LAUNCH_ACTIVITY -> launchActivity(action.data)
            ActionType.SCROLL_UP -> scroll(up = true, percent = scrollBy)
            ActionType.SCROLL_DOWN -> scroll(up = false, percent = scrollBy)
            ActionType.EINK_REFRESH -> einkRefresh()
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
            ActionType.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            ActionType.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            ActionType.VOLUME_MUTE_TOGGLE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            ActionType.DND_TOGGLE -> toggleDoNotDisturb()
            ActionType.BATTERY_SAVER_TOGGLE -> toggleBatterySaver()
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

    /**
     * Starts a specific activity ("pkg/.Class"). Android only lets a normal app
     * start activities the target app exports; internal ones (e.g. ChatGPT's
     * voice mode) can be reached via Shizuku's `am start`, if it is running.
     */
    private fun launchActivity(flattened: String?) {
        val component = flattened?.let { ComponentName.unflattenFromString(it) }
        if (component == null) {
            toast(R.string.error_activity_invalid)
            return
        }
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startActivitySafely(intent)) return
        if (ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()) {
            Thread {
                val ok = ShizukuShell.run("am start -n '${component.flattenToString()}'").ok
                if (!ok) mainHandler.post { toast(R.string.error_activity_failed) }
            }.start()
            return
        }
        toast(R.string.error_activity_failed)
    }

    /** Dispatches a straight-line swipe. Returns false if gestures aren't available. */
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    /** Scrolls the foreground app by [percent] of the screen height. */
    private fun scroll(up: Boolean, percent: Int) {
        val dm = service.resources.displayMetrics
        val x = dm.widthPixels / 2f
        val travel = dm.heightPixels * (percent.coerceIn(10, 90) / 100f)
        val center = dm.heightPixels * 0.52f
        // Finger moves down -> content scrolls up, and vice versa.
        val from = center + if (up) -travel / 2f else travel / 2f
        val to = center + if (up) travel / 2f else -travel / 2f
        // Constant finger speed regardless of distance, so a long swipe does not
        // become a faster one.
        val duration = (travel / dm.heightPixels * 900f).toLong().coerceIn(150L, 900L)
        if (!scrollSwipe(x, from, x, to, duration)) toast(R.string.error_gesture_failed)
    }

    /**
     * A swipe that ends with the finger held still for a moment before lifting.
     * Without that pause Android reads the release velocity as a fling and the
     * app keeps coasting far past the requested distance — the reason scrolling
     * used to overshoot.
     */
    private fun scrollSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val drag = GestureDescription.StrokeDescription(path, 0, durationMs, true)
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = finishScroll(drag, x2, y2)
            override fun onCancelled(gestureDescription: GestureDescription?) = finishScroll(drag, x2, y2)
        }
        return runCatching {
            service.dispatchGesture(
                GestureDescription.Builder().addStroke(drag).build(),
                callback,
                mainHandler,
            )
        }.getOrDefault(false)
    }

    /** Completes a continued stroke; leaving one open would block touch input. */
    private fun finishScroll(drag: GestureDescription.StrokeDescription, x: Float, y: Float) {
        runCatching {
            // A stroke path may not be empty, so the hold moves by a single pixel.
            val hold = Path().apply {
                moveTo(x, y)
                lineTo(x, y + 1f)
            }
            service.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(drag.continueStroke(hold, 0, 120, false))
                    .build(),
                null,
                null,
            )
        }
    }

    /**
     * E-ink full refresh (experimental): replays Bigme's own system gesture —
     * a swipe up from the bottom-right edge.
     */
    private fun einkRefresh() {
        val dm = service.resources.displayMetrics
        val x = dm.widthPixels * 0.92f
        if (!swipe(x, dm.heightPixels - 2f, x, dm.heightPixels * 0.55f, 250)) {
            toast(R.string.error_gesture_failed)
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

    /**
     * Adjusts the music stream and shows the system volume UI, so a remapped key
     * behaves like a real volume rocker. [direction] is one of
     * [AudioManager.ADJUST_RAISE], [AudioManager.ADJUST_LOWER] or
     * [AudioManager.ADJUST_TOGGLE_MUTE].
     */
    private fun adjustVolume(direction: Int) {
        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI,
            )
        }.onFailure { toast(R.string.error_action_failed) }
    }

    private fun toggleBatterySaver() {
        // Fast path: permission held -> instant, safe on the main thread.
        if (PowerSaver.hasWriteSecureSettings(service)) {
            val target = !PowerSaver.isEnabled(service)
            val ok = PowerSaver.setEnabled(service, target)
            toast(
                when {
                    !ok -> R.string.error_action_failed
                    target -> R.string.battery_saver_on
                    else -> R.string.battery_saver_off
                },
            )
            return
        }
        // Shizuku path blocks -> off the main thread, toast back on it.
        if (PowerSaver.canToggle(service)) {
            Thread {
                val result = PowerSaver.toggle(service)
                mainHandler.post {
                    when (result) {
                        true -> toast(R.string.battery_saver_on)
                        false -> toast(R.string.battery_saver_off)
                        null -> toast(R.string.error_action_failed)
                    }
                }
            }.start()
            return
        }
        // Neither available: open the Battery Saver settings page instead.
        if (startActivitySafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))) {
            toast(R.string.battery_saver_manual)
        } else {
            toast(R.string.error_permission_required)
        }
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
