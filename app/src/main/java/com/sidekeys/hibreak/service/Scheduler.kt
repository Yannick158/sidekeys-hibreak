package com.sidekeys.hibreak.service

import android.os.Handler

/**
 * Minimal delayed-execution abstraction so the key gesture state machine can be
 * unit-tested on the JVM with virtual time instead of a real Android Handler.
 */
interface Scheduler {
    fun postDelayed(action: Runnable, delayMs: Long)
    fun cancel(action: Runnable)
}

/** Production implementation backed by the main-thread [Handler]. */
class HandlerScheduler(private val handler: Handler) : Scheduler {
    override fun postDelayed(action: Runnable, delayMs: Long) {
        handler.postDelayed(action, delayMs)
    }

    override fun cancel(action: Runnable) {
        handler.removeCallbacks(action)
    }
}
