package com.portfolio.broker.scheduler

import com.portfolio.broker.service.SnapTradeStatusService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "snaptrade.health-check", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class SnapTradeHealthScheduler(
    private val statusService: SnapTradeStatusService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${snaptrade.health-check.cron:0 */15 * * * *}")
    fun runHealthCheck() {
        log.debug("Running scheduled SnapTrade health check")
        try {
            statusService.checkAndStoreStatus()
        } catch (e: Exception) {
            log.error("Scheduled SnapTrade health check failed: {}", e.message, e)
        }
    }
}
