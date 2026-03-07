package com.portfolio.ingestion.service.etfcom

import com.portfolio.entity.EtfComEnrichmentStatus
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.StepResult
import com.portfolio.repository.EtfRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class EtfComEnrichmentService(
    private val config: IngestionConfig,
    private val etfRepository: EtfRepository,
    private val batchProcessor: EtfComEnrichmentBatchProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun enrichEtfs(step: IngestionStep): StepResult {
        val etfComConfig = config.etfcom
        val batchSize = etfComConfig.batchSize
        val maxRetries = etfComConfig.retry.maxAttempts
        val retryAfter = OffsetDateTime.now().minusMinutes(5)
        val staleThreshold = OffsetDateTime.now().minusDays(etfComConfig.staleThresholdDays.toLong())

        val candidateStatuses = listOf(
            EtfComEnrichmentStatus.PENDING,
            EtfComEnrichmentStatus.FAILED_RETRYABLE,
            EtfComEnrichmentStatus.STALE
        )

        var totalProcessed = 0
        var totalUpdated = 0
        var totalFailed = 0
        var batchNumber = 0

        log.info("Starting etf.com ETF enrichment (batchSize={}, maxRetries={})", batchSize, maxRetries)

        while (true) {
            val candidates = etfRepository.findEtfComEnrichmentCandidates(
                statuses = candidateStatuses,
                maxRetries = maxRetries,
                retryAfter = retryAfter,
                staleThreshold = staleThreshold,
                pageable = PageRequest.of(0, batchSize)
            )

            if (candidates.isEmpty()) {
                log.info("No more etf.com enrichment candidates")
                break
            }

            batchNumber++
            log.info("Processing etf.com enrichment batch {} ({} candidates)", batchNumber, candidates.size)

            val result = batchProcessor.processBatch(candidates, step)
            totalProcessed += result.processed
            totalUpdated += result.updated
            totalFailed += result.failed

            log.info("Batch {} complete: processed={}, updated={}, failed={}",
                batchNumber, result.processed, result.updated, result.failed)

            // Pause between batches to avoid sustained rate limiting from etf.com
            Thread.sleep(etfComConfig.interBatchDelayMs)
        }

        log.info("etf.com ETF enrichment complete: totalProcessed={}, totalUpdated={}, totalFailed={}",
            totalProcessed, totalUpdated, totalFailed)

        return StepResult(
            processed = totalProcessed,
            created = 0,
            updated = totalUpdated,
            failed = totalFailed,
            metadata = mapOf("batches" to batchNumber)
        )
    }
}
