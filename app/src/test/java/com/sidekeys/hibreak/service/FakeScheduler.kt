package com.sidekeys.hibreak.service

/**
 * Test double for [Scheduler] with virtual time — no Robolectric, no mocking
 * library. [advanceBy] runs everything that becomes due, in order.
 */
class FakeScheduler : Scheduler {

    var now = 0L
        private set

    private val pending = mutableListOf<Pair<Long, Runnable>>()

    val pendingCount: Int get() = pending.size

    override fun postDelayed(action: Runnable, delayMs: Long) {
        pending += (now + delayMs) to action
    }

    override fun cancel(action: Runnable) {
        pending.removeAll { it.second === action }
    }

    fun advanceBy(ms: Long) {
        val target = now + ms
        while (true) {
            val next = pending.filter { it.first <= target }.minByOrNull { it.first } ?: break
            pending.remove(next)
            now = next.first
            next.second.run()
        }
        now = target
    }
}
