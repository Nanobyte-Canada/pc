package com.portfolio.broker.scheduler

import com.portfolio.broker.service.ActivityIngestionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "broker.sync",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AccountDataSyncScheduler(
    private val activityIngestionService: ActivityIngestionService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${broker.sync.cron:0 30 22 * * *}")
    fun runNightlySync() {
        log.info("Starting nightly account data sync (activities + balances)")
        try {
            activityIngestionService.syncAllConnections()
        } catch (e: Exception) {
            log.error("Nightly account data sync failed: {}", e.message, e)
        }
    }
}
