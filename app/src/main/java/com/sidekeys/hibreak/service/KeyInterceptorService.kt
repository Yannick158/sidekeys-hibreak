package com.sidekeys.hibreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.sidekeys.hibreak.core.common.KeyCodeNames
import com.sidekeys.hibreak.core.data.Graph
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
data class CapturedKey(val keyCode: Int, val keyName: String)

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

    @Volatile
    private var mappings: Map<Int, KeyMapping> = emptyMap()

    @Volatile
    private var settings: KeySettings = KeySettings()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        executor = ActionExecutor(this)

        // Make sure key filtering is active even if the XML config was ignored.
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        val repository = Graph.mappingRepository(this)
        scope.launch {
            repository.mappings.collect { list ->
                mappings = list.associateBy { it.keyCode }
                // Cancel in-flight gestures of keys whose mapping was removed or
                // emptied, so no stale long-press timer fires later.
                pressHandlers.forEach { (keyCode, pressHandler) ->
                    val mapping = mappings[keyCode]
                    if (mapping == null || mapping.isEmpty) pressHandler.reset()
                }
            }
        }
        scope.launch {
            repository.settings.collect { settings = it }
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

        if (capture) return handleCapture(event)

        // Grace period: swallow all leftovers of a just-captured key.
        captureGraceUntil[event.keyCode]?.let { until ->
            if (event.eventTime <= until) {
                if (event.action == KeyEvent.ACTION_UP) captureConsumedDowns.remove(event.keyCode)
                return true
            }
            captureGraceUntil.remove(event.keyCode)
        }

        // Any event of a key whose DOWN was consumed while capture was active
        // (repeats while held, and the final release).
        if (event.keyCode in captureConsumedDowns) {
            if (event.action == KeyEvent.ACTION_UP) captureConsumedDowns.remove(event.keyCode)
            return true
        }

        val mapping = mappings[event.keyCode]
        if (mapping == null || mapping.isEmpty) {
            // Orphaned UP after we consumed the DOWN (mapping deleted mid-press,
            // capture transition, ...): consume it for symmetry and reset.
            val pressHandler = pressHandlers[event.keyCode]
            if (pressHandler != null && event.action == KeyEvent.ACTION_UP && pressHandler.hasActiveGesture()) {
                pressHandler.reset()
                return true
            }
            return false
        }

        val pressHandler = pressHandlers.getOrPut(event.keyCode) { KeyPressHandler(mainHandler) }
        return when (event.action) {
            KeyEvent.ACTION_DOWN ->
                pressHandler.onDown(mapping, settings, event.repeatCount, event.eventTime, ::runMappedAction)
            KeyEvent.ACTION_UP ->
                pressHandler.onUp(mapping, settings, event.eventTime, ::runMappedAction)
            else -> true
        }
    }

    private fun handleCapture(event: KeyEvent): Boolean {
        if (event.keyCode in KeyCodeNames.BLOCKED_KEY_CODES) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                captureConsumedDowns.add(event.keyCode)
                captureGraceUntil[event.keyCode] = event.eventTime + CAPTURE_GRACE_MS
                capturedKeys.tryEmit(
                    CapturedKey(event.keyCode, KeyCodeNames.prettyName(this, event.keyCode)),
                )
            }
            KeyEvent.ACTION_UP -> captureConsumedDowns.remove(event.keyCode)
        }
        return true
    }

    private fun runMappedAction(action: KeyAction) {
        if (settings.hapticFeedback) executor?.vibrate()
        executor?.execute(action)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

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
        pressHandlers.values.forEach { it.reset() }
        pressHandlers.clear()
        captureConsumedDowns.clear()
        captureGraceUntil.clear()
        executor?.release()
        executor = null
        scope.cancel()
    }
}
