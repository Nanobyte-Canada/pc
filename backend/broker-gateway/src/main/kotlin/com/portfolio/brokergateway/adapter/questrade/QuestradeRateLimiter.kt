package com.portfolio.brokergateway.adapter.questrade

/** Simple fixed-rate pacer: enforces a minimum interval between outbound calls. */
class QuestradeRateLimiter(perSecond: Int) {
    private val minIntervalMs: Long = if (perSecond <= 0) 0 else 1000L / perSecond
    private val lock = Object()
    private var nextAllowedAt = 0L

    fun acquire() {
        if (minIntervalMs <= 0) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = nextAllowedAt - now
            if (wait > 0) {
                Thread.sleep(wait)
                nextAllowedAt += minIntervalMs
            } else {
                nextAllowedAt = now + minIntervalMs
            }
        }
    }
}
