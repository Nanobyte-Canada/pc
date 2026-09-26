package com.portfolio.brokergateway.adapter.questrade

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HttpRetryPolicyTest {
    private val policy = HttpRetryPolicy(maxAttempts = 3, baseDelayMs = 1000, capDelayMs = 60_000)

    @Test
    fun `honors Retry-After over exponential when larger`() {
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = 5, jitter = 0.0)).isEqualTo(5000)
    }
    @Test
    fun `exponential backoff doubles per attempt`() {
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(1000)
        assertThat(policy.delayMs(attempt = 2, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(2000)
        assertThat(policy.delayMs(attempt = 3, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(4000)
    }
    @Test
    fun `caps at 60 seconds`() {
        assertThat(policy.delayMs(attempt = 9, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(60_000)
    }
    @Test
    fun `jitter widens by plus-minus 20 percent`() {
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = null, jitter = 0.2)).isEqualTo(1200)
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = null, jitter = -0.2)).isEqualTo(800)
    }
    @Test
    fun `retryable statuses are 429 and transient 5xx`() {
        assertThat(policy.isRetryable(429)).isTrue()
        assertThat(policy.isRetryable(502)).isTrue()
        assertThat(policy.isRetryable(503)).isTrue()
        assertThat(policy.isRetryable(504)).isTrue()
        assertThat(policy.isRetryable(400)).isFalse()
        assertThat(policy.isRetryable(401)).isFalse()
        assertThat(policy.isRetryable(500)).isFalse()
    }
}
