package com.portfolio.ingestion.scheduler

import com.portfolio.ingestion.pipeline.IngestionOrchestrator
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["ingestion.enabled"], havingValue = "true", matchIfMissing = true)
class IngestionScheduler(
    private val orchestrator: IngestionOrchestrator,
    private val rateLimiter: EodhdRateLimiter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${ingestion.schedule:0 0 22 * * *}")
    fun runNightlyIngestion() {
        log.info("Starting nightly ingestion")
        rateLimiter.resetDailyQuota()
        try {
            runBlocking {
                orchestrator.runFullIngestion("scheduler")
            }
        } catch (e: Exception) {
            log.error("Nightly ingestion failed", e)
        }
    }
}
