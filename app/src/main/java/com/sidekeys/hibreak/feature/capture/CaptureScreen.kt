package com.sidekeys.hibreak.feature.capture

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.repeatOnLifecycle
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.common.KeyCodeNames
import com.sidekeys.hibreak.core.common.rememberServiceRunningState
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.service.CapturedKey
import com.sidekeys.hibreak.service.KeyInterceptorService
import kotlinx.coroutines.delay

/** How long to wait before suggesting that no key events are arriving at all. */
private const val NO_KEY_HINT_MS = 6_000L

/**
 * Pause between detecting a key and moving on, so the user can read which key
 * was detected and the raw numbers behind it. Also plain feedback: on a slow
 * e-ink panel, a screen that changes instantly leaves you unsure it registered
 * the press you meant.
 */
private const val CONFIRM_MS = 900L

/** How long to wait before suggesting the key be held rather than tapped. */
private const val HOLD_HINT_MS = 1_500L

@Composable
fun CaptureScreen(
    onCaptured: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val serviceRunning by rememberServiceRunningState()
    var handled by remember { mutableStateOf(false) }
    var sawAnyKey by remember { mutableStateOf(false) }
    var showNoKeyHint by remember { mutableStateOf(false) }
    var blockedKey by remember { mutableStateOf<CapturedKey?>(null) }
    var lastSeen by remember { mutableStateOf<CapturedKey?>(null) }
    var riskyKey by remember { mutableStateOf<CapturedKey?>(null) }
    var pendingCapture by remember { mutableStateOf<CapturedKey?>(null) }
    var showHoldHint by remember { mutableStateOf(false) }

    // Lifecycle-aware: capture must stop the moment the screen is no longer
    // visible (Home button, screen off), otherwise the service would keep
    // swallowing hardware keys system-wide.
    LifecycleStartEffect(Unit) {
        KeyInterceptorService.captureMode = true
        onStopOrDispose { KeyInterceptorService.captureMode = false }
    }

    // A quick tap is not always enough: some firmwares only report the key once
    // it has been held for a moment. Suggest that before concluding the key
    // cannot be seen at all.
    LaunchedEffect(Unit) {
        delay(HOLD_HINT_MS)
        showHoldHint = !sawAnyKey
    }

    // If nothing arrives at all, the firmware is handling the keys itself —
    // worth saying, because it is the one cause no app can work around.
    LaunchedEffect(Unit) {
        delay(NO_KEY_HINT_MS)
        showNoKeyHint = !sawAnyKey
    }

    LaunchedEffect(pendingCapture) {
        pendingCapture?.let { key ->
            delay(CONFIRM_MS)
            onCaptured(key.keyCode)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            KeyInterceptorService.capturedKeys.collect { captured ->
                sawAnyKey = true
                showNoKeyHint = false
                showHoldHint = false
                lastSeen = captured
                when {
                    captured.blocked -> blockedKey = captured
                    captured.keyCode in KeyCodeNames.RISKY_KEY_CODES -> riskyKey = captured
                    !handled -> {
                        handled = true
                        pendingCapture = captured
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = stringResource(R.string.capture_title), onBack = onCancel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.capture_prompt),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.capture_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            // A slow pulse, so it is obvious the screen is waiting for a press
            // rather than stuck. E-ink cannot do smooth motion, so this is a
            // deliberate two-second fade rather than a spinner.
            if (lastSeen == null) {
                val pulse = rememberInfiniteTransition(label = "waiting")
                val alpha by pulse.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "alpha",
                )
                Text(
                    text = stringResource(
                        if (showHoldHint) R.string.capture_hold_longer else R.string.capture_waiting,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = Color.Black.copy(alpha = alpha),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }

            // Always show the raw values of the last key seen. When two keys look
            // identical, this is the only thing that says whether the device even
            // reports them differently -- and it is readable off the screen, so a
            // user can report it without any tooling.
            lastSeen?.let { key ->
                EInkCard {
                    Text(
                        text = stringResource(R.string.capture_last_seen, key.keyName),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(text = key.detail, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            }

            blockedKey?.let { key ->
                EInkCard {
                    Text(
                        text = stringResource(R.string.capture_blocked_title, key.keyName),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.capture_blocked_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (showNoKeyHint) {
                EInkCard {
                    Text(
                        text = stringResource(R.string.capture_nothing_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.capture_nothing_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!serviceRunning) {
                EInkCard {
                    Text(
                        text = stringResource(R.string.capture_service_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    EInkButton(
                        text = stringResource(R.string.enable_step2),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            EInkOutlinedButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    riskyKey?.let { key ->
        AlertDialog(
            onDismissRequest = { riskyKey = null },
            title = { Text(stringResource(R.string.capture_risky_title, key.keyName)) },
            text = { Text(stringResource(R.string.capture_risky_note)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        riskyKey = null
                        if (!handled) {
                            handled = true
                            onCaptured(key.keyCode)
                        }
                    },
                ) { Text(stringResource(R.string.capture_risky_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { riskyKey = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
