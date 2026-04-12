package com.portfolio.ingestion.provider.eodhd

import com.portfolio.ingestion.config.IngestionProperties
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class EodhdRateLimiter(
    props: IngestionProperties,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ratePerSecond = props.eodhd.rateLimitPerSecond
    private val intervalMs = 1000L / ratePerSecond  // 200ms for 5/sec

    private val dailyQuota = props.eodhd.dailyQuota
    private val dailyUsed = AtomicInteger(0)
    private val waitCounter = Counter.builder("eodhd_rate_limiter_wait_total").register(meterRegistry)

    private val mutex = Mutex()
    private var lastRequestTime = 0L

    suspend fun acquire(apiCost: Int = 1) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < intervalMs) {
                val waitMs = intervalMs - elapsed
                kotlinx.coroutines.delay(waitMs)
            }
            lastRequestTime = System.currentTimeMillis()
            waitCounter.increment()
        }
        dailyUsed.addAndGet(apiCost)
    }

    fun remainingDailyQuota(): Int = (dailyQuota - dailyUsed.get()).coerceAtLeast(0)

    fun recordApiCalls(count: Int) {
        dailyUsed.addAndGet(count)
    }

    fun resetDailyQuota() {
        dailyUsed.set(0)
        log.info("EODHD daily quota reset")
    }
}
