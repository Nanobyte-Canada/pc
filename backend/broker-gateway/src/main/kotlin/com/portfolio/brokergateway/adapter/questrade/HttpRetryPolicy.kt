package com.portfolio.brokergateway.adapter.questrade

/** Pure retry/backoff computation for outbound broker HTTP. Unit: milliseconds. */
class HttpRetryPolicy(
    private val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 1000,
    private val capDelayMs: Long = 60_000,
) {
    fun isRetryable(status: Int): Boolean =
        status == 429 || status == 502 || status == 503 || status == 504

    fun delayMs(attempt: Int, retryAfterSeconds: Int?, jitter: Double): Long {
        val exponential = (baseDelayMs shl (attempt - 1).coerceAtMost(20))
            .coerceAtMost(capDelayMs)
        val chosen = retryAfterSeconds?.let { (it * 1000L).coerceAtMost(capDelayMs) } ?: exponential
        return (chosen * (1.0 + jitter)).toLong().coerceIn(0, capDelayMs)
    }

    fun jitter(): Double = (random.nextDouble() * 0.4) - 0.2 // ±20%
    fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts

    private companion object { val random = java.util.Random() }
}
