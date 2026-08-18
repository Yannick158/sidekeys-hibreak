package com.sidekeys.hibreak

import android.app.ActivityManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                SideKeysApp()
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
