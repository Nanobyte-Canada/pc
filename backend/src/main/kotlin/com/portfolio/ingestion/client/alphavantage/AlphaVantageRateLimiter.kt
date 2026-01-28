package com.portfolio.ingestion.client.alphavantage

import com.portfolio.ingestion.config.IngestionConfig
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Rate limiter for Alpha Vantage API.
 *
 * Enforces three limits:
 * 1. Per-second limit (evenly distributes requests to avoid burst patterns)
 * 2. Per-minute limit (default: 75 requests/minute for premium tier)
 * 3. Daily quota (default: unlimited for premium tier, set dailyQuota <= 0)
 *
 * Free tier: 5 requests/minute, 25 requests/day
 * Premium tiers: 75-1200 requests/minute, unlimited daily quota
 *
 * The per-second throttle prevents burst patterns that trigger API errors like:
 * "Burst pattern detected. Please consider spreading out your API requests."
 *
 * Supports both blocking and non-blocking (coroutine) modes:
 * - Use `acquire()` for blocking calls (backward compatible)
 * - Use `acquireAsync()` for non-blocking coroutine-based calls
 */
@Component
class AlphaVantageRateLimiter(
    private val config: IngestionConfig,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Per-second throttling (to avoid burst patterns)
    private val requestsPerSecond: Int
        get() = config.alphavantage.rateLimit.requestsPerSecond
    private val requestsThisSecond = AtomicInteger(0)
    private val lastSecondReset = AtomicReference(System.currentTimeMillis())

    // Per-minute tracking
    private val requestsThisMinute = AtomicInteger(0)
    private val lastMinuteReset = AtomicReference(System.currentTimeMillis())

    // Daily tracking
    private val requestsToday = AtomicInteger(0)
    private val lastDayReset = AtomicReference(LocalDate.now())

    private val waitTimeCounter = Counter.builder("av_rate_limiter_wait_ms_total")
        .description("Total milliseconds spent waiting for rate limiter")
        .register(meterRegistry)

    private val perSecondWaitCounter = Counter.builder("av_rate_limiter_per_second_wait_total")
        .description("Number of times per-second throttle triggered")
        .register(meterRegistry)

    private val dailyQuotaExhausted = Counter.builder("av_daily_quota_exhausted_total")
        .description("Number of times daily quota was exhausted")
        .register(meterRegistry)

    /**
     * Semaphore to control concurrent API requests.
     * Initialized to the per-second request limit to prevent burst patterns.
     */
    val concurrencySemaphore: Semaphore by lazy {
        Semaphore(requestsPerSecond)
    }

    /**
     * Acquires a permit asynchronously, suspending if necessary until rate limits allow.
     * Returns false if daily quota is exhausted.
     *
     * Uses per-second throttling to evenly distribute requests and avoid burst patterns.
     * This is the preferred method for coroutine-based callers.
     */
    suspend fun acquireAsync(): Boolean {
        // First check daily quota
        resetIfNeeded()
        val dailyQuota = config.alphavantage.dailyQuota

        if (dailyQuota > 0 && requestsToday.get() >= dailyQuota) {
            log.warn("Daily quota exhausted ({}/{})", requestsToday.get(), dailyQuota)
            dailyQuotaExhausted.increment()
            return false
        }

        // Per-second throttling to avoid burst patterns
        while (true) {
            val now = System.currentTimeMillis()
            val lastReset = lastSecondReset.get()
            val elapsed = now - lastReset

            // Reset counter if 1 second has passed
            if (elapsed >= 1000) {
                if (lastSecondReset.compareAndSet(lastReset, now)) {
                    requestsThisSecond.set(0)
                }
                continue // Re-check after reset
            }

            // Check if we can make a request this second
            val current = requestsThisSecond.get()
            if (current < requestsPerSecond) {
                if (requestsThisSecond.compareAndSet(current, current + 1)) {
                    requestsThisMinute.incrementAndGet()
                    requestsToday.incrementAndGet()

                    log.debug("Permit acquired. Requests this second: {}/{}, minute: {}, today: {}",
                        current + 1, requestsPerSecond, requestsThisMinute.get(), requestsToday.get())
                    return true
                }
                // CAS failed, retry loop
            } else {
                // Wait until next second tick
                val waitTime = 1000 - elapsed + 50 // 50ms buffer
                if (waitTime > 0) {
                    log.debug("Per-second limit reached ({}/{}), waiting {}ms",
                        current, requestsPerSecond, waitTime)
                    perSecondWaitCounter.increment()
                    waitTimeCounter.increment(waitTime.toDouble())
                    delay(waitTime) // Non-blocking delay
                }
            }
        }
    }

    /**
     * Acquires a permit, blocking if necessary until rate limits allow.
     * Returns false if daily quota is exhausted.
     *
     * This method provides backward compatibility for non-coroutine callers.
     * For coroutine-based code, prefer `acquireAsync()`.
     */
    fun acquire(): Boolean = runBlocking {
        acquireAsync()
    }

    /**
     * Executes a suspend block with concurrency control.
     * Acquires a permit from the semaphore before executing.
     */
    suspend fun <T> withConcurrencyLimit(block: suspend () -> T): T {
        return concurrencySemaphore.withPermit {
            block()
        }
    }

    /**
     * Returns the number of requests remaining today.
     * Returns Int.MAX_VALUE if daily quota is unlimited (dailyQuota <= 0).
     */
    fun remainingDailyQuota(): Int {
        resetIfNeeded()
        val dailyQuota = config.alphavantage.dailyQuota
        return if (dailyQuota <= 0) Int.MAX_VALUE else max(0, dailyQuota - requestsToday.get())
    }

    /**
     * Returns whether the daily quota is exhausted.
     * Always returns false if daily quota is unlimited (dailyQuota <= 0).
     */
    fun isDailyQuotaExhausted(): Boolean {
        resetIfNeeded()
        val dailyQuota = config.alphavantage.dailyQuota
        return dailyQuota > 0 && requestsToday.get() >= dailyQuota
    }

    /**
     * Returns the current requests made today.
     */
    fun requestsMadeToday(): Int {
        resetIfNeeded()
        return requestsToday.get()
    }

    private fun resetIfNeeded() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        // Reset second counter every 1 second
        val lastSecond = lastSecondReset.get()
        if (now - lastSecond >= 1000) {
            if (lastSecondReset.compareAndSet(lastSecond, now)) {
                requestsThisSecond.set(0)
            }
        }

        // Reset minute counter every 60 seconds
        if (now - lastMinuteReset.get() >= 60_000) {
            requestsThisMinute.set(0)
            lastMinuteReset.set(now)
            log.debug("Minute counter reset")
        }

        // Reset daily counter at midnight
        if (today != lastDayReset.get()) {
            requestsToday.set(0)
            lastDayReset.set(today)
            log.info("Daily counter reset for new day: {}", today)
        }
    }
}
