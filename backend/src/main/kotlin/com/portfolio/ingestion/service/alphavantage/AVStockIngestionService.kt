package com.portfolio.ingestion.service.alphavantage

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
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

/**
 * Service for ingesting raw Alpha Vantage data for Stock entities.
 *
 * Fetches raw data from the Alpha Vantage OVERVIEW endpoint and stores the JSON payload.
 * Uses hash-based change detection to skip re-fetching unchanged payloads.
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

        val avConfig = config.alphavantage
        val batchSize = avConfig.batchSize

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

        var page = 0
        var totalBatches = 0

        while (true) {
            remainingQuota = avClient.remainingDailyQuota()
            if (remainingQuota <= 0) {
                log.info("Quota exhausted. Stopping stock ingestion.")
                quotaExhausted.increment()
                break
            }

            val pageRequest = PageRequest.of(page, batchSize, Sort.by("id"))
            val batch = stockRepository.findAll(pageRequest)
            if (!batch.hasContent()) break
            page++

            totalBatches++
            log.info("Processing batch {} with {} stocks for AV ingestion", totalBatches, batch.content.size)

            try {
                val result = batchProcessor.processBatch(batch.content, step)
                updated += result.updated
                failed += result.failed
                processed += result.processed

                if (result.quotaExhausted) {
                    quotaExhausted.increment()
                    break
                }

                log.info("Batch {} committed: updated={}, failed={}, remaining_quota={}",
                    totalBatches, result.updated, result.failed, avClient.remainingDailyQuota())
            } catch (e: Exception) {
                log.error("Batch {} failed and was rolled back: {}", totalBatches, e.message)
                failed += batch.content.size
                processed += batch.content.size
            }
        }

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
}
