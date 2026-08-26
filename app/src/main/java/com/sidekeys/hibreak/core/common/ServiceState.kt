package com.sidekeys.hibreak.core.common

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.sidekeys.hibreak.service.KeyInterceptorService
import kotlinx.coroutines.delay

/** Polls the accessibility-service state (the system offers no callback for it). */
@Composable
fun rememberServiceRunningState(): State<Boolean> {
    val state = remember { mutableStateOf(KeyInterceptorService.isRunning) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = KeyInterceptorService.isRunning
            delay(750)
        }
    }
    return state
}

/** What state the accessibility service is actually in. */
enum class ServiceHealth {
    /** Bound and intercepting keys. */
    RUNNING,

    /**
     * Android lists SideKeys as an enabled accessibility service, but nothing is
     * running. Reinstalling leaves the old entry behind — most often after
     * switching between the Play build and the GitHub APK, which forces an
     * uninstall because the two are signed differently. The switch in system
     * settings then shows as already on, so there is nothing obvious to turn on,
     * and every key silently does nothing.
     */
    ENABLED_BUT_DEAD,

    /** Not enabled at all — the ordinary first-run state. */
    OFF,
}

/**
 * Polls the real state rather than just the app's own flag, so the case where
 * the system and the service disagree can be told apart and explained.
 */
@Composable
fun rememberServiceHealth(): State<ServiceHealth> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(ServiceHealth.OFF) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = when {
                KeyInterceptorService.isRunning -> ServiceHealth.RUNNING
                isListedAsEnabled(context) -> ServiceHealth.ENABLED_BUT_DEAD
                else -> ServiceHealth.OFF
            }
            delay(750)
        }
    }
    return state
}

/**
 * Whether the system currently counts this app among the enabled accessibility
 * services. Uses the public AccessibilityManager list rather than reading the
 * secure setting directly, which newer Android versions refuse to hand out.
 */
private fun isListedAsEnabled(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
}.getOrDefault(false)
