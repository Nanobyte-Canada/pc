package com.portfolio.marketdata.distribution

import com.portfolio.marketdata.config.ExpiryProperties
import com.portfolio.marketdata.ibkr.IbkrClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ExpiryRefreshService(
    private val ibkrClient: IbkrClient,
    private val expiryCacheService: ExpiryCacheService,
    private val properties: ExpiryProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Refresh expiry dates on application startup so the cache is populated
     * immediately rather than waiting for the first scheduled Monday run.
     * Runs with a short delay to let IBKR connection stabilize.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        Thread {
            try {
                Thread.sleep(10_000) // Wait 10s for IBKR connection to stabilize
                log.info("Startup expiry refresh triggered")
                refreshAll()
            } catch (e: Exception) {
                log.warn("Startup expiry refresh failed (will retry on schedule): {}", e.message)
            }
        }.apply {
            isDaemon = true
            name = "expiry-startup-refresh"
            start()
        }
    }

    @Scheduled(cron = "\${expiry.refresh.cron:0 0 8 ? * MON}")
    fun refreshAll() {
        log.info("Starting scheduled expiry refresh for {} symbols", properties.refresh.symbols.size)
        var successCount = 0
        var failCount = 0

        for (symbol in properties.refresh.symbols) {
            try {
                refreshSymbol(symbol)
                successCount++
            } catch (e: Exception) {
                log.error("Failed to refresh expirations for {}: {}", symbol, e.message)
                failCount++
            }
        }

        log.info("Expiry refresh complete: {} succeeded, {} failed", successCount, failCount)
    }

    fun refreshSymbol(symbol: String) {
        log.debug("Refreshing expirations for {}", symbol)
        try {
            val expirations = ibkrClient.requestOptionExpirations(symbol)
            expiryCacheService.cacheExpiry(symbol, expirations)
            log.debug("Cached {} expirations for {}", expirations.size, symbol)
        } catch (e: Exception) {
            log.error("Failed to refresh expirations for {}: {}", symbol, e.message)
        }
    }
}
