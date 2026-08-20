package com.sidekeys.hibreak.feature.capture

import android.content.Intent
import android.provider.Settings
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
    var riskyKey by remember { mutableStateOf<CapturedKey?>(null) }

    // Lifecycle-aware: capture must stop the moment the screen is no longer
    // visible (Home button, screen off), otherwise the service would keep
    // swallowing hardware keys system-wide.
    LifecycleStartEffect(Unit) {
        KeyInterceptorService.captureMode = true
        onStopOrDispose { KeyInterceptorService.captureMode = false }
    }

    // If nothing arrives at all, the firmware is handling the keys itself —
    // worth saying, because it is the one cause no app can work around.
    LaunchedEffect(Unit) {
        delay(NO_KEY_HINT_MS)
        showNoKeyHint = !sawAnyKey
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            KeyInterceptorService.capturedKeys.collect { captured ->
                sawAnyKey = true
                showNoKeyHint = false
                when {
                    captured.blocked -> blockedKey = captured
                    captured.keyCode in KeyCodeNames.RISKY_KEY_CODES -> riskyKey = captured
                    !handled -> {
                        handled = true
                        onCaptured(captured.keyCode)
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
            Spacer(Modifier.height(32.dp))

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
