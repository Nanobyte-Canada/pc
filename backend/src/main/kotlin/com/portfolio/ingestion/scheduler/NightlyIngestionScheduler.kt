package com.portfolio.ingestion.scheduler

import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.service.IngestionOrchestrator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "ingestion", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class NightlyIngestionScheduler(
    private val orchestrator: IngestionOrchestrator,
    private val config: IngestionConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs the full ingestion pipeline on schedule.
     * Default: Daily at 10:00 PM (22:00)
     * Configurable via ingestion.schedule property
     */
    @Scheduled(cron = "\${ingestion.schedule:0 0 22 * * *}")
    fun runNightlyIngestion() {
        log.info("=".repeat(60))
        log.info("Starting scheduled nightly ingestion job")
        log.info("=".repeat(60))

        try {
            val run = orchestrator.runFullIngestion("scheduler")
            log.info("Nightly ingestion completed: runId=${run.id}, status=${run.status}")
        } catch (e: Exception) {
            log.error("Nightly ingestion failed: ${e.message}", e)
        }

        log.info("=".repeat(60))
        log.info("Finished scheduled nightly ingestion job")
        log.info("=".repeat(60))
    }
}
