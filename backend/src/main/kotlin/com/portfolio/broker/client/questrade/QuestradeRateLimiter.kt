package com.portfolio.broker.client.questrade

import com.portfolio.broker.config.BrokerConfig
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Token bucket rate limiter for Questrade API calls.
 * Questrade has strict rate limits (1 request per second typically).
 */
@Component
class QuestradeRateLimiter(
    private val config: BrokerConfig,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val rateLimitConfig = config.questrade.rateLimit
    private val tokensPerSecond = rateLimitConfig.requestsPerSecond
    private val maxTokens = rateLimitConfig.burstSize.toDouble()

    private var tokens = maxTokens
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    private val waitCounter = meterRegistry.counter("questrade_rate_limit_waits_total")
    private val acquireCounter = meterRegistry.counter("questrade_rate_limit_acquires_total")

    /**
     * Acquire a token for making an API request.
     * Blocks until a token is available.
     */
    suspend fun acquire() {
        acquireCounter.increment()

        synchronized(this) {
            refillTokens()

            while (tokens < 1.0) {
                val waitTimeMs = ((1.0 - tokens) / tokensPerSecond * 1000).toLong()
                val actualWait = max(10, min(waitTimeMs, 2000))

                log.debug("Rate limit: waiting {}ms for token (current: {})", actualWait, tokens)
                waitCounter.increment()

                // Release lock while waiting
                kotlinx.coroutines.runBlocking {
                    delay(actualWait)
                }

                refillTokens()
            }

            tokens -= 1.0
        }
    }

    /**
     * Try to acquire a token without blocking.
     * Returns true if a token was acquired, false otherwise.
     */
    fun tryAcquire(): Boolean {
        synchronized(this) {
            refillTokens()

            if (tokens >= 1.0) {
                tokens -= 1.0
                acquireCounter.increment()
                return true
            }

            return false
        }
    }

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val lastRefill = lastRefillTime.get()
        val elapsedMs = now - lastRefill

        if (elapsedMs > 0) {
            val tokensToAdd = (elapsedMs / 1000.0) * tokensPerSecond
            tokens = min(maxTokens, tokens + tokensToAdd)
            lastRefillTime.set(now)
        }
    }

    /**
     * Get current available tokens (for monitoring).
     */
    fun availableTokens(): Double {
        synchronized(this) {
            refillTokens()
            return tokens
        }
    }
}
