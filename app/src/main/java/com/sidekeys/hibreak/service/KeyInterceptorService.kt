package com.sidekeys.hibreak.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.session.PlaybackState
import android.media.session.MediaSession
import android.media.VolumeProvider
import android.media.AudioManager
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
data class CapturedKey(
    val keyCode: Int,
    val keyName: String,
    val blocked: Boolean = false,
    /**
     * Raw numbers behind the name, e.g. "key code 0, scan code 143". Shown in
     * the capture screen because when two physical keys arrive looking alike,
     * these are the only values that say whether the device distinguishes them
     * at all — and a user can read them off the screen and report them.
     */
    val detail: String = "",
)

/** The raw identity of a key event, for reporting what a device actually sends. */
fun KeyEvent.identityDetail(): String = "key code $keyCode, scan code $scanCode"

/**
 * Accessibility service that filters hardware key events and maps the
 * Bigme HiBreak Pro side keys (or any other hardware key) to user actions.
 */
class KeyInterceptorService : AccessibilityService() {

    companion object {
        private const val TAG = "SideKeys"
        /**
         * How long a capture without a real key code waits for a proper one.
         * Long enough to catch the paired event some firmwares send, short
         * enough that a device which only ever sends the raw event still feels
         * immediate.
         */
        private const val PHANTOM_GRACE_MS = 120L

        /**
         * How long after the last tick the synthetic release fires. Longer
         * than typical volume auto-repeat (50–125 ms), so a held key stays one
         * gesture; short enough that a deliberate second tap lands after the
         * release and reads as a double press.
         */
        private const val SYNTH_UP_MS = 130L

        /** Hidden but long-stable system broadcast and its extras (API 21+). */
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val EXTRA_VOLUME_STREAM_PREV_VALUE =
            "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"

        /** How long our own volume writes blind the watcher. */
        private const val SELF_CHANGE_SUPPRESS_MS = 400L

        /**
         * Debounce floor on the audio route: the gap between a synthetic
         * release and a genuine second tap must clear it, so it is kept low.
         * The user's own setting still applies when larger.
         */
        private const val AUDIO_DEBOUNCE_FLOOR_MS = 60L

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
    private var duraSpeedObserver: ContentObserver? = null

    /**
     * Id chosen at DOWN, kept until the matching UP, keyed by the raw key code.
     *
     * [KeyCodeNames.keyIdOf] falls back to the scan code, and a device does not
     * have to report the scan code on every event of a press. If DOWN resolved
     * to a scan-code id and UP did not, the release would be filed under a
     * different key: the press handler would never see it, so a long-press timer
     * would hang and the next press would be swallowed as a leftover. The key
     * would simply stop working.
     */
    private val activeKeyIds = HashMap<Int, Int>()

    /**
     * A capture held back because it carried no real key code.
     *
     * Some firmwares emit two events for one press: the key the user configured
     * in system settings, and a raw one the vendor sends alongside it. On a
     * Bigme B7 Pro both page-turn keys emit the same raw event (scan code 143),
     * so grabbing whichever arrives first can land on the one that cannot tell
     * the keys apart. Waiting a moment for a real key code and preferring that
     * keeps the two keys separable.
     */
    private var pendingPhantom: Runnable? = null

    /**
     * Volume-key capture through the audio system — the route of last resort
     * for firmwares that consume volume keys before input dispatch.
     *
     * A MediaSession with [MediaSession.setPlaybackToRemote] makes the system
     * deliver volume presses as [VolumeProvider.onAdjustVolume] callbacks
     * instead of changing a stream volume. Tasker and Key Mapper use the same
     * mechanism for their screen-off volume triggers.
     *
     * No double-firing with the normal path by construction: when the key
     * filter receives a volume key and a mapping consumes it, the event never
     * reaches the audio system, so this callback does not fire. This path only
     * sees what the filter could not.
     */
    private var volumeSession: MediaSession? = null

    /**
     * One in-flight gesture per volume key on the audio route.
     *
     * The audio route delivers bare ticks, not down/up pairs, so the release
     * is synthesised: the DOWN goes to the state machine on the first tick,
     * and the UP fires [SYNTH_UP_MS] after the *last* tick — every further
     * tick just postpones it. A held key auto-repeats faster than that, so to
     * the state machine it looks like one continuous hold, and the ordinary
     * long-press timer fires mid-hold. Single and double press fall out of the
     * same machinery unchanged.
     */
    private class AudioGesture(val handler: KeyPressHandler) {
        var synthUp: Runnable? = null
    }

    private val audioGestures = HashMap<Int, AudioGesture>()

    /**
     * Second engine of the audio route: observe the *effect* of a volume key
     * instead of the key itself.
     *
     * The MediaSession path only fires when the firmware routes volume keys
     * through the media-session service. A vendor that handles them internally
     * (adjusting the stream directly — the Viwoods pattern) bypasses it, and no
     * app ever sees the key. But the volume still changes, and the system
     * announces every change as a broadcast with the previous and new value.
     * Direction read from the delta, volume snapped back, and the press is
     * recovered — nothing intercepted at all.
     *
     * Exactly one engine fires per press by construction: when the session
     * receives the key, the stream volume never changes, so there is no
     * broadcast; when the firmware adjusts the stream directly, the session
     * stays silent and the broadcast fires.
     */
    private var volumeWatcher: BroadcastReceiver? = null

    /**
     * Ignore volume broadcasts until this uptime: they are echoes of our own
     * writes (the snap-back, or an executed volume action), not key presses.
     */
    @Volatile
    private var volumeSelfChangeUntil = 0L

    private val audioSettings: KeySettings
        get() = settings.let { it.copy(debounceMs = maxOf(it.debounceMs, AUDIO_DEBOUNCE_FLOOR_MS)) }

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
                applyDuraSpeedGuard(it.keepDuraSpeedOff)
                applyVolumeCapture(it.volumeAudioCapture, it.volumeChangeObserver)
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

        val keyId = stableKeyId(event)
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

    /**
     * The id for this event, pinned to whatever DOWN decided so a press and its
     * release always resolve to the same key.
     */
    private fun stableKeyId(event: KeyEvent): Int {
        val raw = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            return KeyCodeNames.keyIdOf(event).also { activeKeyIds[raw] = it }
        }
        val id = activeKeyIds[raw] ?: KeyCodeNames.keyIdOf(event)
        if (event.action == KeyEvent.ACTION_UP) activeKeyIds.remove(raw)
        return id
    }

    /** Cancels a held-back capture, because something better arrived. */
    private fun dropPendingPhantom() {
        pendingPhantom?.let { mainHandler.removeCallbacks(it) }
        pendingPhantom = null
    }

    private fun handleCapture(event: KeyEvent, keyId: Int): Boolean {
        if (event.keyCode in KeyCodeNames.BLOCKED_KEY_CODES) {
            // Say what arrived rather than ignoring it. A capture screen that
            // never reacts is indistinguishable from one that receives nothing,
            // and the difference is exactly what needs diagnosing.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                capturedKeys.tryEmit(
                    CapturedKey(
                        keyId,
                        KeyCodeNames.prettyName(this, keyId),
                        blocked = true,
                        detail = event.identityDetail(),
                    ),
                )
            }
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                captureConsumedDowns.add(keyId)
                captureGraceUntil[keyId] = event.eventTime + CAPTURE_GRACE_MS
                val captured =
                    CapturedKey(keyId, KeyCodeNames.prettyName(this, keyId), detail = event.identityDetail())
                if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    // Might be the vendor's raw twin of a real key. Hold it back
                    // briefly; if a proper key code follows it wins, because only
                    // that one distinguishes one page key from the other.
                    dropPendingPhantom()
                    val emit = Runnable {
                        pendingPhantom = null
                        capturedKeys.tryEmit(captured)
                    }
                    pendingPhantom = emit
                    mainHandler.postDelayed(emit, PHANTOM_GRACE_MS)
                } else {
                    dropPendingPhantom()
                    capturedKeys.tryEmit(captured)
                }
            }
            KeyEvent.ACTION_UP -> captureConsumedDowns.remove(keyId)
        }
        return true
    }

    /**
     * Watches DuraSpeed and switches it back off whenever something turns it
     * on. A one-off write is not enough: on some firmwares the value comes back
     * after a reboot or a system update, and by then the service is already
     * being killed again without the user knowing why.
     */
    private fun applyDuraSpeedGuard(enabled: Boolean) {
        if (!enabled) {
            duraSpeedObserver?.let { contentResolver.unregisterContentObserver(it) }
            duraSpeedObserver = null
            return
        }
        // Runs on every service start, which is the moment that matters: after a
        // reboot the stored value reads 0 while DuraSpeed is up and running
        // again, so the value alone would never trigger anything.
        if (duraSpeedObserver != null) {
            enforceDuraSpeedOff()
            return
        }
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) = enforceDuraSpeedOff()
        }
        runCatching {
            contentResolver.registerContentObserver(DuraSpeed.globalUri(), false, observer)
            duraSpeedObserver = observer
        }
        enforceDuraSpeedOff()
    }

    private fun enforceDuraSpeedOff() {
        // Skip the changes we caused ourselves, otherwise the write and the
        // watcher chase each other.
        if (DuraSpeed.isApplying()) return
        // Blocks on the Shizuku path, so never on the main thread.
        scope.launch(Dispatchers.IO) {
            val context = this@KeyInterceptorService
            if (DuraSpeed.canWrite(context) && DuraSpeed.needsDisabling(context)) {
                DuraSpeed.disable(context)
            }
        }
    }

    private fun applyVolumeCapture(enabled: Boolean, observer: Boolean) {
        if (!enabled) {
            volumeSession?.release()
            volumeSession = null
        }
        // The watcher is its own opt-in on top of the audio route. On a device
        // whose volume keys arrive through the normal filter it adds nothing
        // but side effects, so it must never ride along silently.
        if (!enabled || !observer) {
            volumeWatcher?.let { runCatching { unregisterReceiver(it) } }
            volumeWatcher = null
        }
        if (!enabled) return
        if (observer && volumeWatcher == null) {
            val watcher = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == VOLUME_CHANGED_ACTION) onVolumeChanged(intent)
                }
            }
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    watcher,
                    IntentFilter(VOLUME_CHANGED_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                volumeWatcher = watcher
            }
        }
        if (volumeSession != null) return
        runCatching {
            val session = MediaSession(this, "SideKeys volume capture")
            // STATE_PLAYING gives the session volume-routing priority. It takes
            // no audio focus, so real playback elsewhere is not interrupted —
            // but while another app is actively playing, its session wins the
            // keys, which is documented in the setting's description.
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build(),
            )
            session.setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 2, 1) {
                override fun onAdjustVolume(direction: Int) {
                    // 0 is ADJUST_SAME (key release on some devices) — ignore.
                    if (direction != 0) mainHandler.post { onVolumeTick(direction) }
                }
            })
            session.isActive = true
            volumeSession = session
        }
    }

    /** A volume change observed on the media stream — engine 2's input. */
    private fun onVolumeChanged(intent: Intent) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now < volumeSelfChangeUntil) return

        val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
        if (stream != AudioManager.STREAM_MUSIC) return
        val newValue = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
        val prevValue = intent.getIntExtra(EXTRA_VOLUME_STREAM_PREV_VALUE, -1)
        if (newValue < 0 || prevValue < 0 || newValue == prevValue) return

        // Engaged only when a volume key is actually mapped — otherwise stock
        // behaviour must stay untouched, slider drags included.
        val upMapped = resolveMapping(KeyEvent.KEYCODE_VOLUME_UP)
            ?.let { !it.isEmpty && !it.isPassThrough } == true
        val downMapped = resolveMapping(KeyEvent.KEYCODE_VOLUME_DOWN)
            ?.let { !it.isEmpty && !it.isPassThrough } == true
        val direction = if (newValue > prevValue) 1 else -1
        if (!(if (direction > 0) upMapped else downMapped)) return

        // While something is really playing, a volume change is what the user
        // wanted; hijacking it would break every music app.
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (runCatching { audio.isMusicActive }.getOrDefault(false)) return

        // Undo the change so the press reads as a key, not as volume. Clamped
        // one step away from both ends: at the limit a press changes nothing,
        // sends no broadcast, and would be lost.
        val max = runCatching { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15)
        val restore = prevValue.coerceIn(1, (max - 1).coerceAtLeast(1))
        volumeSelfChangeUntil = now + SELF_CHANGE_SUPPRESS_MS
        runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0)
        }

        onVolumeTick(direction)
    }

    /**
     * A volume press that arrived through the audio route.
     *
     * An empty single-press slot falls back to the plain volume action, so
     * "single press changes the volume, double press does something else"
     * needs nothing more than leaving the single slot unassigned.
     */
    private fun onVolumeTick(direction: Int) {
        val keyCode =
            if (direction > 0) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN

        if (captureMode) {
            capturedKeys.tryEmit(
                CapturedKey(
                    keyCode,
                    KeyCodeNames.prettyName(this, keyCode),
                    detail = "key code $keyCode, via audio route",
                ),
            )
            return
        }

        val volumeFallback = KeyAction(
            if (direction > 0) ActionType.VOLUME_UP else ActionType.VOLUME_DOWN,
        )

        val mapping = resolveMapping(keyCode)
        if (mapping == null || mapping.isEmpty || mapping.isPassThrough) {
            // No mapping at all: stay a stock volume key, tick for tick, so a
            // held key still ramps the volume. The state machine is engaged
            // only when there is a real mapping to disambiguate for.
            runMappedAction(volumeFallback)
            return
        }

        // An unassigned single slot means "keep changing the volume" — the
        // state machine skips NONE actions, so substitute the volume action.
        val effective = if (mapping.singlePress.type == ActionType.NONE) {
            mapping.copy(singlePress = volumeFallback)
        } else {
            mapping
        }

        val gesture = audioGestures.getOrPut(keyCode) {
            AudioGesture(KeyPressHandler(HandlerScheduler(mainHandler)))
        }
        val runner: (KeyAction) -> Unit = { runMappedAction(it, effective.scrollPercent) }

        gesture.synthUp?.let { pending ->
            // The key is considered held; this tick is its auto-repeat.
            // Postpone the synthetic release — the DOWN stays pressed, so the
            // ordinary long-press timer keeps running.
            mainHandler.removeCallbacks(pending)
        } ?: run {
            gesture.handler.onDown(
                effective,
                audioSettings,
                0,
                android.os.SystemClock.uptimeMillis(),
                runner,
            )
        }
        val release = Runnable {
            gesture.synthUp = null
            gesture.handler.onUp(
                effective,
                audioSettings,
                android.os.SystemClock.uptimeMillis(),
                runner,
            )
        }
        gesture.synthUp = release
        mainHandler.postDelayed(release, SYNTH_UP_MS)
    }

    private fun runMappedAction(action: KeyAction, scrollPercent: Int? = null) {
        // A volume action changes the stream we watch; its broadcast is an
        // echo of ours, not a key press.
        if (action.type == ActionType.VOLUME_UP || action.type == ActionType.VOLUME_DOWN ||
            action.type == ActionType.VOLUME_MUTE_TOGGLE
        ) {
            volumeSelfChangeUntil =
                android.os.SystemClock.uptimeMillis() + SELF_CHANGE_SUPPRESS_MS
        }
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
        // Registered against the ContentResolver, which outlives the service —
        // leaving it holds a reference to a dead service.
        duraSpeedObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        duraSpeedObserver = null
        runCatching { unregisterReceiver(batteryReceiver) }
        pressHandlers.values.forEach { it.reset() }
        pressHandlers.clear()
        activeKeyIds.clear()
        dropPendingPhantom()
        volumeSession?.release()
        volumeSession = null
        volumeWatcher?.let { runCatching { unregisterReceiver(it) } }
        volumeWatcher = null
        audioGestures.values.forEach { gesture ->
            gesture.synthUp?.let { mainHandler.removeCallbacks(it) }
            gesture.handler.reset()
        }
        audioGestures.clear()
        captureConsumedDowns.clear()
        captureGraceUntil.clear()
        executor?.release()
        executor = null
    }
}
