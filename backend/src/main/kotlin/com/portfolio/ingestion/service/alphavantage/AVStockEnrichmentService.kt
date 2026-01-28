package com.portfolio.ingestion.service.alphavantage

import com.portfolio.entity.AVEnrichmentStatus
import com.portfolio.entity.AVIngestionStatus
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.ingestion.service.StepResult
import com.portfolio.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * Service for enriching Stock entities from stored Alpha Vantage raw payloads.
 *
 * This service reads from the avRawPayload field (which was populated by AVStockIngestionService)
 * and parses the JSON to map fields to entity columns.
 *
 * It does NOT call the Alpha Vantage API - that's the ingestion service's job.
 *
 * Enrichment includes:
 * - Company description, sector, industry
 * - Financial metrics (P/E ratio, market cap, EPS, etc.)
 * - Analyst ratings
 * - Price metrics (52-week high/low, moving averages)
 *
 * Uses batch-based transaction commits for resilience:
 * - Each batch is committed independently via REQUIRES_NEW in the batch processor
 * - If a batch fails, previously committed batches are not rolled back
 */
@Service
class AVStockEnrichmentService(
    private val config: IngestionConfig,
    private val stockRepository: StockRepository,
    private val batchProcessor: AVStockEnrichmentBatchProcessor,
    private val trackingService: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Enriches stocks from stored Alpha Vantage raw payloads.
     *
     * Reads the avRawPayload field and parses it to map fields to entity columns.
     * Does NOT call the API - the raw data must already be ingested.
     *
     * Each batch is committed independently, so failures only affect the current batch.
     *
     * @param step The ingestion step for tracking
     * @return StepResult with counts of processed, updated, and failed records
     */
    fun enrichStocks(step: IngestionStep): StepResult {
        var processed = 0
        var updated = 0
        var failed = 0

        val avConfig = config.alphavantage
        val batchSize = avConfig.batchSize
        val maxRetries = avConfig.retry.maxAttempts
        val retryAfter = calculateRetryAfter()
        val staleThreshold = OffsetDateTime.now().minusDays(avConfig.staleThresholdDays.toLong())

        val enrichmentStatuses = listOf(
            AVEnrichmentStatus.PENDING,
            AVEnrichmentStatus.FAILED_RETRYABLE,
            AVEnrichmentStatus.STALE
        )

        log.info("Starting AV stock enrichment from stored payloads")

        var totalBatches = 0

        // Process all candidates in batches
        do {
            // Fetch next batch of candidates that have ingested data ready for enrichment
            val batch = stockRepository.findAvEnrichmentCandidatesWithIngestionSuccess(
                ingestionStatus = AVIngestionStatus.SUCCESS,
                enrichmentStatuses = enrichmentStatuses,
                maxRetries = maxRetries,
                retryAfter = retryAfter,
                staleThreshold = staleThreshold,
                pageable = PageRequest.of(0, batchSize)
            )

            if (batch.isEmpty()) {
                if (totalBatches == 0) {
                    log.info("No stocks pending Alpha Vantage enrichment with ingested data")
                }
                break
            }

            totalBatches++
            log.info("Processing batch {} with {} stocks for AV enrichment", totalBatches, batch.size)

            try {
                // Each batch commits independently via REQUIRES_NEW
                val result = batchProcessor.processBatch(batch, step)
                updated += result.updated
                failed += result.failed
                processed += result.processed

                log.info("Batch {} committed: updated={}, failed={}",
                    totalBatches, result.updated, result.failed)
            } catch (e: Exception) {
                // Batch failed and rolled back, but previous batches are safe
                log.error("Batch {} failed and was rolled back: {}", totalBatches, e.message)
                failed += batch.size
                processed += batch.size
            }

        } while (true)

        log.info("AV Stock enrichment complete: total_batches={}, processed={}, updated={}, failed={}",
            totalBatches, processed, updated, failed)
        return StepResult(
            processed = processed,
            created = 0,
            updated = updated,
            failed = failed,
            metadata = mapOf(
                "type" to "stocks",
                "total_batches" to totalBatches
            )
        )
    }

    private fun calculateRetryAfter(): OffsetDateTime {
        val backoffMs = config.alphavantage.retry.initialBackoffMs
        return OffsetDateTime.now().minusSeconds(backoffMs / 1000)
    }
}
