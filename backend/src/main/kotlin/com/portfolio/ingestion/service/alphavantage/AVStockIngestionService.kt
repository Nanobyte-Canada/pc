package com.portfolio.ingestion.service.alphavantage

import com.portfolio.entity.AVIngestionStatus
import com.portfolio.ingestion.client.alphavantage.AlphaVantageClient
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.ingestion.service.StepResult
import com.portfolio.repository.StockRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * Service for ingesting raw Alpha Vantage data for Stock entities.
 *
 * This service only fetches data from the Alpha Vantage OVERVIEW endpoint
 * and stores the raw JSON payload. It does NOT parse or map fields to entity columns.
 *
 * The enrichment step (AVStockEnrichmentService) is responsible for parsing
 * the stored raw payload and mapping fields to the entity.
 *
 * Uses batch-based transaction commits for resilience:
 * - Each batch is committed independently via REQUIRES_NEW in the batch processor
 * - If a batch fails, previously committed batches are not rolled back
 */
@Service
class AVStockIngestionService(
    private val config: IngestionConfig,
    private val avClient: AlphaVantageClient,
    private val stockRepository: StockRepository,
    private val batchProcessor: AVStockIngestionBatchProcessor,
    private val trackingService: IngestionTrackingService,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val quotaExhausted = Counter.builder("av_ingestion_quota_exhausted_total")
        .tag("type", "stock")
        .register(meterRegistry)

    /**
     * Ingests raw Alpha Vantage data for stocks.
     *
     * Fetches from AV OVERVIEW endpoint and stores raw JSON only.
     * Does NOT parse or map fields - that's the enrichment service's job.
     *
     * Each batch is committed independently, so failures only affect the current batch.
     */
    fun ingestStocks(step: IngestionStep): StepResult {
        var processed = 0
        var updated = 0
        var failed = 0
        var quotaExhaustedInRun = false

        val avConfig = config.alphavantage
        val batchSize = avConfig.batchSize
        val maxRetries = avConfig.retry.maxAttempts
        val retryAfter = calculateRetryAfter()
        val staleThreshold = OffsetDateTime.now().minusDays(avConfig.staleThresholdDays.toLong())

        val statuses = listOf(
            AVIngestionStatus.PENDING,
            AVIngestionStatus.FAILED_RETRYABLE,
            AVIngestionStatus.STALE
        )

        // Check if we have quota remaining
        var remainingQuota = avClient.remainingDailyQuota()
        if (remainingQuota <= 0) {
            log.warn("Daily API quota exhausted. Skipping stock ingestion.")
            quotaExhausted.increment()
            return StepResult(
                processed = 0,
                created = 0,
                updated = 0,
                failed = 0,
                metadata = mapOf("type" to "stocks", "skipped" to "quota_exhausted")
            )
        }

        log.info("Starting AV stock ingestion. Remaining daily quota: {}", remainingQuota)

        var totalBatches = 0

        // Process all candidates in batches
        do {
            // Re-check quota before each batch
            remainingQuota = avClient.remainingDailyQuota()
            if (remainingQuota <= 0) {
                log.info("Daily quota exhausted. Stopping stock ingestion.")
                break
            }

            // Fetch next batch of candidates
            val batch = stockRepository.findAvIngestionCandidates(
                statuses = statuses,
                maxRetries = maxRetries,
                retryAfter = retryAfter,
                staleThreshold = staleThreshold,
                pageable = PageRequest.of(0, minOf(batchSize, remainingQuota))
            )

            if (batch.isEmpty()) {
                if (totalBatches == 0) {
                    log.info("No stocks pending Alpha Vantage ingestion")
                }
                break
            }

            totalBatches++
            log.info("Processing batch {} with {} stocks for AV ingestion", totalBatches, batch.size)

            try {
                // Each batch commits independently via REQUIRES_NEW
                val result = batchProcessor.processBatch(batch, step)
                updated += result.updated
                failed += result.failed
                processed += result.processed

                if (result.quotaExhausted) {
                    quotaExhaustedInRun = true
                    quotaExhausted.increment()
                }

                log.info("Batch {} committed: updated={}, failed={}, remaining_quota={}",
                    totalBatches, result.updated, result.failed, avClient.remainingDailyQuota())
            } catch (e: Exception) {
                // Batch failed and rolled back, but previous batches are safe
                log.error("Batch {} failed and was rolled back: {}", totalBatches, e.message)
                failed += batch.size
                processed += batch.size
            }

        } while (!quotaExhaustedInRun)

        log.info("AV Stock ingestion complete: total_batches={}, processed={}, updated={}, failed={}",
            totalBatches, processed, updated, failed)
        return StepResult(
            processed = processed,
            created = 0,
            updated = updated,
            failed = failed,
            metadata = mapOf(
                "type" to "stocks",
                "total_batches" to totalBatches,
                "remaining_quota" to avClient.remainingDailyQuota()
            )
        )
    }

    private fun calculateRetryAfter(): OffsetDateTime {
        val backoffMs = config.alphavantage.retry.initialBackoffMs
        return OffsetDateTime.now().minusSeconds(backoffMs / 1000)
    }
}
