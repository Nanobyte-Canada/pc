package com.portfolio.marketdata.distribution

import com.portfolio.marketdata.config.ExpiryProperties
import com.portfolio.marketdata.ibkr.IbkrClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ExpiryRefreshService(
    private val ibkrClient: IbkrClient,
    private val expiryCacheService: ExpiryCacheService,
    private val properties: ExpiryProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
