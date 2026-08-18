package com.sidekeys.hibreak

import android.app.ActivityManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import androidx.lifecycle.lifecycleScope
import com.sidekeys.hibreak.core.common.KeyCodeNames
import com.sidekeys.hibreak.core.data.Graph
import com.sidekeys.hibreak.core.designsystem.SideKeysTheme
import com.sidekeys.hibreak.service.CapturedKey
import com.sidekeys.hibreak.service.KeyInterceptorService
import com.sidekeys.hibreak.ui.SideKeysApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SideKeysTheme {
                // If the previous run crashed, show the report first so the
                // problem can be diagnosed straight from the phone.
                var crash by rememberSaveable { mutableStateOf(SideKeysApplication.readCrash(this)) }
                val report = crash
                if (report != null) {
                    CrashReportScreen(
                        report = report,
                        onDismiss = {
                            SideKeysApplication.clearCrash(this)
                            crash = null
                        },
                    )
                } else {
                    SideKeysApp()
                }
            }
        }
        // Bigme's task manager force-stops apps swiped from recents, which kills
        // the accessibility service. Staying out of the recents list (default)
        // means there is nothing to swipe away.
        lifecycleScope.launch {
            Graph.mappingRepository(applicationContext).settings.collect { settings ->
                applyExcludeFromRecents(settings.hideFromRecents)
            }
        }
    }

    private fun applyExcludeFromRecents(hide: Boolean) {
        runCatching {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { it.setExcludeFromRecents(hide) }
        }
    }

    /**
     * Fallback key capture: when the accessibility service is not running,
     * hardware keys still reach the focused activity, so the capture screen
     * works before the service has been enabled.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (KeyInterceptorService.captureMode &&
            !KeyInterceptorService.isRunning &&
            keyCode !in KeyCodeNames.BLOCKED_KEY_CODES
        ) {
            if (event == null || event.repeatCount == 0) {
                KeyInterceptorService.capturedKeys.tryEmit(
                    CapturedKey(keyCode, KeyCodeNames.prettyName(this, keyCode)),
                )
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (KeyInterceptorService.captureMode &&
            !KeyInterceptorService.isRunning &&
            keyCode !in KeyCodeNames.BLOCKED_KEY_CODES
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

/** Plain, dependency-light screen — it must not be able to crash itself. */
@Composable
private fun CrashReportScreen(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "SideKeys ist beim letzten Start abgestürzt",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Bitte den Bericht kopieren und weitergeben — er enthält nur den Fehlerort im Code, keine persönlichen Daten.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = report,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(12.dp))
        Row {
            EInkButton(
                text = "Bericht kopieren",
                onClick = { clipboard.setText(AnnotatedString(report)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            EInkOutlinedButton(
                text = "Weiter",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
