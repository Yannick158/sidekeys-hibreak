package com.sidekeys.hibreak.core.common

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
