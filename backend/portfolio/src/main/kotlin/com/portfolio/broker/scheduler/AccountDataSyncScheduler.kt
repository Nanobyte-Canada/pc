package com.portfolio.broker.scheduler

import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.service.ActivityIngestionService
import com.portfolio.broker.service.PortfolioSnapshotService
import com.portfolio.broker.service.PositionFetchService
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
    private val activityIngestionService: ActivityIngestionService,
    private val positionFetchService: PositionFetchService,
    private val connectionRepository: BrokerConnectionRepository,
    private val snapshotService: PortfolioSnapshotService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${broker.sync.cron:0 20 16 * * *}")
    fun runPostMarketSync() {
        log.info("Starting post-market data sync (4:20 PM)")
        runSync()
    }

    @Scheduled(cron = "\${broker.sync.cron-morning:0 0 6 * * *}")
    fun runMorningSync() {
        log.info("Starting morning data sync (6:00 AM)")
        runSync()
    }

    private fun runSync() {
        try {
            activityIngestionService.syncAllConnections()
        } catch (e: Exception) {
            log.error("Activity/balance sync failed: {}", e.message, e)
        }

        try {
            val activeConnections = connectionRepository.findByStatus(ConnectionStatus.ACTIVE)
            log.info("Fetching positions for {} active connections", activeConnections.size)
            for (conn in activeConnections) {
                try {
                    positionFetchService.triggerManualFetch(conn.id, conn.user.id)
                } catch (e: Exception) {
                    log.warn("Position fetch failed for connection {}: {}", conn.id, e.message)
                }
            }
        } catch (e: Exception) {
            log.error("Position sync failed: {}", e.message, e)
        }

        try {
            snapshotService.takeSnapshotsForAllGroups()
        } catch (e: Exception) {
            log.error("Snapshot creation failed: {}", e.message, e)
        }

        log.info("Data sync complete")
    }
}
