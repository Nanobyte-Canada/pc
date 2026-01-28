package com.portfolio.ingestion.service.alphavantage

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.AVEnrichmentStatus
import com.portfolio.entity.AVIngestionStatus
import com.portfolio.entity.Stock
import com.portfolio.ingestion.client.alphavantage.AVApiResult
import com.portfolio.ingestion.client.alphavantage.AlphaVantageClient
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.BatchResult
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.StockRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Batch processor for stock ingestion with per-batch transaction commits.
 *
 * Uses REQUIRES_NEW propagation so each batch is committed independently.
 * This ensures that if a batch fails, previously processed batches remain committed.
 *
 * Handles concurrent API calls within each batch using coroutines.
 */
@Service
class AVStockIngestionBatchProcessor(
    private val config: IngestionConfig,
    private val avClient: AlphaVantageClient,
    private val stockRepository: StockRepository,
    private val trackingService: IngestionTrackingService,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val ingestionSuccess = Counter.builder("av_ingestion_total")
        .tag("type", "stock")
        .tag("status", "success")
        .register(meterRegistry)

    private val ingestionFailed = Counter.builder("av_ingestion_total")
        .tag("type", "stock")
        .tag("status", "failed")
        .register(meterRegistry)

    private val concurrencySemaphore: Semaphore by lazy {
        Semaphore(config.alphavantage.rateLimit.requestsPerSecond)
    }

    /**
     * Processes a batch of stocks with an independent transaction.
     *
     * Each call to this method runs in its own transaction (REQUIRES_NEW),
     * so it will commit when the method returns, regardless of what happens
     * in subsequent batches.
     *
     * @return BatchResult with counts and a flag indicating if quota was exhausted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processBatch(batch: List<Stock>, step: IngestionStep): IngestionBatchResult {
        val processed = AtomicInteger(0)
        val updated = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val quotaExhaustedFlag = AtomicBoolean(false)

        // Process batch concurrently using coroutines
        runBlocking(Dispatchers.IO) {
            batch.map { stock ->
                async {
                    if (quotaExhaustedFlag.get()) {
                        return@async
                    }

                    concurrencySemaphore.withPermit {
                        if (avClient.isDailyQuotaExhausted()) {
                            if (quotaExhaustedFlag.compareAndSet(false, true)) {
                                log.warn("Daily quota exhausted mid-batch. Stopping further requests.")
                            }
                            return@withPermit
                        }

                        try {
                            val ingested = ingestStockAsync(stock, step)
                            if (ingested) {
                                updated.incrementAndGet()
                                ingestionSuccess.increment()
                            } else {
                                failed.incrementAndGet()
                                ingestionFailed.increment()
                            }
                        } catch (e: Exception) {
                            log.error("Unexpected error ingesting stock {}: {}", stock.ticker, e.message)
                            handleIngestionError(stock, e, step)
                            failed.incrementAndGet()
                            ingestionFailed.increment()
                        }
                        processed.incrementAndGet()
                    }
                }
            }.awaitAll()
        }

        // Commits when this method returns due to REQUIRES_NEW
        return IngestionBatchResult(
            updated = updated.get(),
            failed = failed.get(),
            processed = processed.get(),
            quotaExhausted = quotaExhaustedFlag.get()
        )
    }

    private suspend fun ingestStockAsync(stock: Stock, step: IngestionStep): Boolean {
        stock.avIngestionLastAttemptAt = OffsetDateTime.now()

        val result = avClient.getStockOverviewAsync(stock.ticker)

        return when (result) {
            is AVApiResult.Success -> {
                stock.avRawPayload = try {
                    objectMapper.writeValueAsString(result.data)
                } catch (e: Exception) {
                    log.error("Failed to serialize response for {}: {}", stock.ticker, e.message)
                    null
                }

                stock.avIngestionStatus = AVIngestionStatus.SUCCESS
                stock.avIngestionLastSuccessAt = OffsetDateTime.now()
                stock.avIngestionRetryCount = 0
                stock.avIngestionErrorCode = null
                stock.avIngestionErrorMessage = null
                stock.avEnrichmentStatus = AVEnrichmentStatus.PENDING

                stockRepository.save(stock)
                log.debug("Successfully ingested raw data for stock {}", stock.ticker)
                true
            }

            is AVApiResult.NotFound -> {
                log.debug("Stock {} not found in Alpha Vantage", stock.ticker)
                stock.avIngestionStatus = AVIngestionStatus.FAILED_PERMANENT
                stock.avIngestionErrorCode = "NOT_FOUND"
                stock.avIngestionErrorMessage = "Symbol not found in Alpha Vantage"
                stockRepository.save(stock)
                false
            }

            is AVApiResult.RateLimited -> {
                log.warn("Rate limited while ingesting stock {}", stock.ticker)
                stock.avIngestionStatus = AVIngestionStatus.FAILED_RETRYABLE
                stock.avIngestionRetryCount = stock.avIngestionRetryCount + 1
                stock.avIngestionErrorCode = "RATE_LIMITED"
                stock.avIngestionErrorMessage = result.message
                stockRepository.save(stock)
                false
            }

            is AVApiResult.QuotaExhausted -> {
                log.warn("Daily quota exhausted while ingesting stock {}", stock.ticker)
                stock.avIngestionErrorCode = "QUOTA_EXHAUSTED"
                stock.avIngestionErrorMessage = result.message
                stockRepository.save(stock)
                false
            }

            is AVApiResult.Error -> {
                val isRetryable = avClient.isRetryableError(result)
                stock.avIngestionErrorCode = result.statusCode?.toString() ?: "UNKNOWN"
                stock.avIngestionErrorMessage = result.exception.message?.take(500)

                if (isRetryable) {
                    stock.avIngestionStatus = AVIngestionStatus.FAILED_RETRYABLE
                    stock.avIngestionRetryCount = stock.avIngestionRetryCount + 1
                } else {
                    stock.avIngestionStatus = AVIngestionStatus.FAILED_PERMANENT
                }

                stockRepository.save(stock)

                trackingService.logError(
                    stepId = step.id,
                    errorType = ErrorType.API_ERROR,
                    message = result.exception.message ?: "Unknown API error",
                    context = mapOf<String, Any>(
                        "ticker" to stock.ticker,
                        "statusCode" to (result.statusCode?.toString() ?: "unknown")
                    )
                )
                false
            }
        }
    }

    private fun handleIngestionError(stock: Stock, e: Exception, step: IngestionStep) {
        stock.avIngestionStatus = AVIngestionStatus.FAILED_RETRYABLE
        stock.avIngestionRetryCount = stock.avIngestionRetryCount + 1
        stock.avIngestionErrorCode = "UNKNOWN"
        stock.avIngestionErrorMessage = e.message?.take(500)
        stockRepository.save(stock)

        trackingService.logError(
            stepId = step.id,
            errorType = ErrorType.API_ERROR,
            message = e.message ?: "Unknown error",
            context = mapOf<String, Any>("ticker" to stock.ticker)
        )
    }
}

/**
 * Extended batch result for ingestion that includes quota exhaustion status.
 */
data class IngestionBatchResult(
    val updated: Int = 0,
    val failed: Int = 0,
    val processed: Int = 0,
    val quotaExhausted: Boolean = false
)
