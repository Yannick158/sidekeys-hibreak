package com.sidekeys.hibreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.sidekeys.hibreak.core.common.KeyCodeNames
import com.sidekeys.hibreak.core.data.Graph
import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.ChargeSettings
import com.sidekeys.hibreak.core.model.KeyAction
import com.sidekeys.hibreak.core.model.KeyMapping
import com.sidekeys.hibreak.core.model.KeySettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** A key press observed while the capture screen is open. */
data class CapturedKey(val keyCode: Int, val keyName: String, val blocked: Boolean = false)

/**
 * Accessibility service that filters hardware key events and maps the
 * Bigme HiBreak Pro side keys (or any other hardware key) to user actions.
 */
class KeyInterceptorService : AccessibilityService() {

    companion object {
        private const val TAG = "SideKeys"
        private const val CAPTURE_GRACE_MS = 700L

        @Volatile
        var isRunning: Boolean = false
            private set

        /** While true, the next key press is reported to [capturedKeys] instead of being mapped. */
        @Volatile
        var captureMode: Boolean = false

        val capturedKeys = MutableSharedFlow<CapturedKey>(extraBufferCapacity = 8)

        @Volatile
        private var instance: KeyInterceptorService? = null

        /** Lets the UI trigger an action for testing. Returns false if the service is off. */
        fun runAction(action: KeyAction): Boolean {
            val service = instance ?: return false
            service.mainHandler.post { service.executor?.execute(action) }
            return true
        }
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "collector failed", e) },
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pressHandlers = mutableMapOf<Int, KeyPressHandler>()

    /** Keys whose DOWN was consumed by the capture screen; their UP must be consumed too. */
    private val captureConsumedDowns = mutableSetOf<Int>()

    /**
     * Keys captured moments ago: every event (auto-repeat while held, bounce,
     * an immediate second press) is swallowed for a short grace period so
     * nothing leaks to the system/app while the mapping screen is opening.
     */
    private val captureGraceUntil = mutableMapOf<Int, Long>()
    private var lastCaptureMode = false

    private var executor: ActionExecutor? = null

    /** All mappings (global + per-app), grouped by key code. */
    @Volatile
    private var mappingsByKey: Map<Int, List<KeyMapping>> = emptyMap()

    /** Package of the foreground activity, tracked via window-state events. */
    @Volatile
    private var foregroundPackage: String? = null

    /** Cache: is (package, class) an Activity? Avoids repeated PackageManager lookups. */
    private val activityClassCache = mutableMapOf<String, Boolean>()

    @Volatile
    private var settings: KeySettings = KeySettings()

    @Volatile
    private var chargeSettings: ChargeSettings = ChargeSettings()

    /** True once the charge alarm has fired for the current charging session. */
    @Volatile
    private var alarmedThisCharge = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleBattery(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        executor = ActionExecutor(this)

        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Make sure key filtering is active even if the XML config was ignored.
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        val repository = Graph.mappingRepository(this)
        scope.launch {
            repository.mappings.collect { list ->
                mappingsByKey = list.groupBy { it.keyCode }
                // Cancel in-flight gestures of keys whose mapping was removed or
                // emptied, so no stale long-press timer fires later.
                pressHandlers.forEach { (keyCode, pressHandler) ->
                    val mapping = resolveMapping(keyCode)
                    if (mapping == null || mapping.isEmpty) pressHandler.reset()
                }
            }
        }
        scope.launch {
            repository.settings.collect {
                settings = it
                executor?.scrollPercent = it.scrollPercent
            }
        }
        scope.launch {
            repository.chargeSettings.collect { chargeSettings = it }
        }
    }

    /**
     * Effective mapping for [keyId]: the foreground app's profile (if any)
     * merged slot-by-slot over the global mapping; null if neither exists.
     *
     * Falls back to [rawKeyCode] when nothing is mapped to the precise key.
     * That covers two cases at once. Mappings saved before scan codes were used
     * as identity sit under the raw key code, so an existing setup keeps
     * working after an update. And on devices whose side keys all report
     * KEYCODE_UNKNOWN, one mapping saved under that code drives every one of
     * them — which some people prefer, since both keys doing the same thing is
     * a legitimate setup, not a bug. Assigning a key individually stores it
     * under its own id, and that takes precedence.
     */
    private fun resolveMapping(keyId: Int, rawKeyCode: Int = keyId): KeyMapping? {
        val candidates = mappingsByKey[keyId]
            ?: mappingsByKey[rawKeyCode]
            ?: return null
        val global = candidates.firstOrNull { it.packageName == null }
        val fg = foregroundPackage
        val perApp = if (fg != null) candidates.firstOrNull { it.packageName == fg } else null
        return perApp?.mergedOver(global) ?: global
    }

    /**
     * Charge alarm: when plugged in and the target level is reached, alert once
     * per charging session so the user can unplug. Works on any device — no root
     * or writable charging node required.
     */
    private fun handleBattery(intent: Intent) {
        val cfg = chargeSettings
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0

        if (cfg.alarmEnabled && plugged && percent >= cfg.alarmPercent) {
            if (!alarmedThisCharge) {
                alarmedThisCharge = true
                ChargeAlarm.alert(this, percent)
            }
        } else if (!plugged || percent < cfg.alarmPercent) {
            alarmedThisCharge = false
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // On any capture-mode transition, kill in-flight gesture timers so a
        // pending long/single press cannot fire across the transition.
        val capture = captureMode
        if (capture != lastCaptureMode) {
            lastCaptureMode = capture
            pressHandlers.values.forEach { it.reset() }
        }

        val keyId = KeyCodeNames.keyIdOf(event)
        if (capture) return handleCapture(event, keyId)

        // Grace period: swallow all leftovers of a just-captured key.
        captureGraceUntil[keyId]?.let { until ->
            if (event.eventTime <= until) {
                if (event.action == KeyEvent.ACTION_UP) captureConsumedDowns.remove(keyId)
                return true
            }
            captureGraceUntil.remove(keyId)
        }

        // Any event of a key whose DOWN was consumed while capture was active
        // (repeats while held, and the final release).
        if (keyId in captureConsumedDowns) {
            if (event.action == KeyEvent.ACTION_UP) captureConsumedDowns.remove(keyId)
            return true
        }

        val mapping = resolveMapping(keyId, event.keyCode)
        if (mapping == null || mapping.isEmpty || mapping.isPassThrough) {
            // Orphaned UP after we consumed the DOWN (mapping deleted mid-press,
            // capture transition, ...): consume it for symmetry and reset.
            val pressHandler = pressHandlers[keyId]
            if (pressHandler != null && event.action == KeyEvent.ACTION_UP && pressHandler.hasActiveGesture()) {
                pressHandler.reset()
                return true
            }
            return false
        }

        val pressHandler = pressHandlers.getOrPut(keyId) { KeyPressHandler(HandlerScheduler(mainHandler)) }
        return when (event.action) {
            KeyEvent.ACTION_DOWN ->
                pressHandler.onDown(mapping, settings, event.repeatCount, event.eventTime) {
                    runMappedAction(it, mapping.scrollPercent)
                }
            KeyEvent.ACTION_UP ->
                pressHandler.onUp(mapping, settings, event.eventTime) {
                    runMappedAction(it, mapping.scrollPercent)
                }
            else -> true
        }
    }

    private fun handleCapture(event: KeyEvent, keyId: Int): Boolean {
        if (event.keyCode in KeyCodeNames.BLOCKED_KEY_CODES) {
            // Say what arrived rather than ignoring it. A capture screen that
            // never reacts is indistinguishable from one that receives nothing,
            // and the difference is exactly what needs diagnosing.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                capturedKeys.tryEmit(
                    CapturedKey(keyId, KeyCodeNames.prettyName(this, keyId), blocked = true),
                )
            }
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                captureConsumedDowns.add(keyId)
                captureGraceUntil[keyId] = event.eventTime + CAPTURE_GRACE_MS
                capturedKeys.tryEmit(
                    CapturedKey(keyId, KeyCodeNames.prettyName(this, keyId)),
                )
            }
            KeyEvent.ACTION_UP -> captureConsumedDowns.remove(keyId)
        }
        return true
    }

    private fun runMappedAction(action: KeyAction, scrollPercent: Int? = null) {
        // A blocked key should feel like a dead key, not like a triggered one.
        if (settings.hapticFeedback && action.type != ActionType.BLOCK) executor?.vibrate()
        executor?.execute(action, scrollPercent)
    }

    /**
     * Tracks the foreground app for per-app profiles. Only the package name of
     * window-state changes is used — no screen content is read. IME/popup
     * windows are ignored by checking that the class is an Activity.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (pkg == packageName) return
        val key = "$pkg/$cls"
        val isActivity = activityClassCache.getOrPut(key) {
            runCatching {
                packageManager.getActivityInfo(android.content.ComponentName(pkg, cls), 0)
                true
            }.getOrDefault(false)
        }
        if (isActivity) foregroundPackage = pkg
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        if (instance === this) {
            instance = null
            isRunning = false
        }
        scope.cancel()
        runCatching { unregisterReceiver(batteryReceiver) }
        pressHandlers.values.forEach { it.reset() }
        pressHandlers.clear()
        captureConsumedDowns.clear()
        captureGraceUntil.clear()
        executor?.release()
        executor = null
    }
}
