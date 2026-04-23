// adapter/wealthsimple/WealthsimpleRateLimiter.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerRateLimitException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque

class WealthsimpleRateLimiter(
    private val maxOrdersPerHour: Int = 7
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val orderTimestamps = ConcurrentLinkedDeque<Long>()

    fun checkOrderAllowed() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600_000L

        while (orderTimestamps.peekFirst()?.let { it < oneHourAgo } == true) {
            orderTimestamps.pollFirst()
        }

        if (orderTimestamps.size >= maxOrdersPerHour) {
            val oldestInWindow = orderTimestamps.peekFirst() ?: now
            val retryAfterSeconds = ((oldestInWindow + 3600_000L - now) / 1000).toInt().coerceAtLeast(1)
            log.warn("Wealthsimple order rate limit reached: {}/{} in last hour", orderTimestamps.size, maxOrdersPerHour)
            throw BrokerRateLimitException(
                "Wealthsimple order rate limit: $maxOrdersPerHour orders per hour exceeded",
                BrokerType.WEALTHSIMPLE, retryAfterSeconds = retryAfterSeconds)
        }
    }

    fun recordOrder() {
        orderTimestamps.addLast(System.currentTimeMillis())
    }

    fun getOrdersInLastHour(): Int {
        val oneHourAgo = System.currentTimeMillis() - 3600_000L
        while (orderTimestamps.peekFirst()?.let { it < oneHourAgo } == true) {
            orderTimestamps.pollFirst()
        }
        return orderTimestamps.size
    }
}
