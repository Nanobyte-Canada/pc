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

    @Scheduled(cron = "\${broker.sync.cron:0 30 22 * * *}")
    fun runNightlySync() {
        log.info("Starting nightly account data sync (activities + balances + positions)")
        try {
            activityIngestionService.syncAllConnections()
        } catch (e: Exception) {
            log.error("Nightly activity/balance sync failed: {}", e.message, e)
        }

        try {
            val activeConnections = connectionRepository.findByStatus(ConnectionStatus.ACTIVE)
            log.info("Fetching positions for {} active connections", activeConnections.size)
            for (conn in activeConnections) {
                try {
                    positionFetchService.triggerManualFetch(conn.id, conn.user.id)
                    log.info("Nightly position fetch completed for connection {}", conn.id)
                } catch (e: Exception) {
                    log.warn("Nightly position fetch failed for connection {}: {}", conn.id, e.message)
                }
            }
        } catch (e: Exception) {
            log.error("Nightly position sync failed: {}", e.message, e)
        }

        // Take daily snapshots for all portfolio groups (for performance tracking)
        try {
            snapshotService.takeSnapshotsForAllGroups()
        } catch (e: Exception) {
            log.error("Nightly snapshot creation failed: {}", e.message, e)
        }
    }
}
