// test: adapter/wealthsimple/WealthsimpleRateLimiterTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.exception.BrokerRateLimitException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WealthsimpleRateLimiterTest {

    @Test
    fun `allows orders under the limit`() {
        val limiter = WealthsimpleRateLimiter(maxOrdersPerHour = 3)
        assertDoesNotThrow { limiter.checkOrderAllowed() }
        limiter.recordOrder()
        assertDoesNotThrow { limiter.checkOrderAllowed() }
        limiter.recordOrder()
        assertDoesNotThrow { limiter.checkOrderAllowed() }
        limiter.recordOrder()
    }

    @Test
    fun `throws when limit is reached`() {
        val limiter = WealthsimpleRateLimiter(maxOrdersPerHour = 2)
        limiter.recordOrder()
        limiter.recordOrder()
        val ex = assertThrows<BrokerRateLimitException> { limiter.checkOrderAllowed() }
        assertTrue(ex.retryAfterSeconds != null && ex.retryAfterSeconds!! > 0)
    }

    @Test
    fun `getOrdersInLastHour tracks count`() {
        val limiter = WealthsimpleRateLimiter(maxOrdersPerHour = 7)
        assertEquals(0, limiter.getOrdersInLastHour())
        limiter.recordOrder()
        limiter.recordOrder()
        assertEquals(2, limiter.getOrdersInLastHour())
    }
}
