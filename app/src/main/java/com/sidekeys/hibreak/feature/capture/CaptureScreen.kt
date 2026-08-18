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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.sidekeys.hibreak.core.common.rememberServiceRunningState
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.service.KeyInterceptorService

@Composable
fun CaptureScreen(
    onCaptured: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val serviceRunning by rememberServiceRunningState()
    var handled by remember { mutableStateOf(false) }

    // Lifecycle-aware: capture must stop the moment the screen is no longer
    // visible (Home button, screen off), otherwise the service would keep
    // swallowing hardware keys system-wide.
    LifecycleStartEffect(Unit) {
        KeyInterceptorService.captureMode = true
        onStopOrDispose { KeyInterceptorService.captureMode = false }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            KeyInterceptorService.capturedKeys.collect { captured ->
                if (!handled) {
                    handled = true
                    onCaptured(captured.keyCode)
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
}
