package com.portfolio.ingestion.service.alphavantage

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.AVEnrichmentStatus
import com.portfolio.entity.AVIngestionStatus
import com.portfolio.entity.Etf
import com.portfolio.ingestion.client.alphavantage.AVApiResult
import com.portfolio.ingestion.client.alphavantage.AlphaVantageClient
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.EtfRepository
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
 * Batch processor for ETF ingestion with per-batch transaction commits.
 *
 * Uses REQUIRES_NEW propagation so each batch is committed independently.
 * This ensures that if a batch fails, previously processed batches remain committed.
 *
 * Handles concurrent API calls within each batch using coroutines.
 */
@Service
class AVEtfIngestionBatchProcessor(
    private val config: IngestionConfig,
    private val avClient: AlphaVantageClient,
    private val etfRepository: EtfRepository,
    private val trackingService: IngestionTrackingService,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val ingestionSuccess = Counter.builder("av_ingestion_total")
        .tag("type", "etf")
        .tag("status", "success")
        .register(meterRegistry)

    private val ingestionFailed = Counter.builder("av_ingestion_total")
        .tag("type", "etf")
        .tag("status", "failed")
        .register(meterRegistry)

    private val concurrencySemaphore: Semaphore by lazy {
        Semaphore(config.alphavantage.rateLimit.requestsPerSecond)
    }

    /**
     * Processes a batch of ETFs with an independent transaction.
     *
     * Each call to this method runs in its own transaction (REQUIRES_NEW),
     * so it will commit when the method returns, regardless of what happens
     * in subsequent batches.
     *
     * @return IngestionBatchResult with counts and a flag indicating if quota was exhausted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processBatch(batch: List<Etf>, step: IngestionStep): IngestionBatchResult {
        val processed = AtomicInteger(0)
        val updated = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val quotaExhaustedFlag = AtomicBoolean(false)

        // Process batch concurrently using coroutines
        runBlocking(Dispatchers.IO) {
            batch.map { etf ->
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
                            val ingested = ingestEtfAsync(etf, step)
                            if (ingested) {
                                updated.incrementAndGet()
                                ingestionSuccess.increment()
                            } else {
                                failed.incrementAndGet()
                                ingestionFailed.increment()
                            }
                        } catch (e: Exception) {
                            log.error("Unexpected error ingesting ETF {}: {}", etf.symbol, e.message)
                            handleIngestionError(etf, e, step)
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

    private suspend fun ingestEtfAsync(etf: Etf, step: IngestionStep): Boolean {
        etf.avIngestionLastAttemptAt = OffsetDateTime.now()

        val result = avClient.getEtfProfileAsync(etf.symbol)

        return when (result) {
            is AVApiResult.Success -> {
                etf.avRawPayload = try {
                    objectMapper.writeValueAsString(result.data)
                } catch (e: Exception) {
                    log.error("Failed to serialize response for {}: {}", etf.symbol, e.message)
                    null
                }

                etf.avIngestionStatus = AVIngestionStatus.SUCCESS
                etf.avIngestionLastSuccessAt = OffsetDateTime.now()
                etf.avIngestionRetryCount = 0
                etf.avIngestionErrorCode = null
                etf.avIngestionErrorMessage = null
                etf.avEnrichmentStatus = AVEnrichmentStatus.PENDING

                etfRepository.save(etf)
                log.debug("Successfully ingested raw data for ETF {}", etf.symbol)
                true
            }

            is AVApiResult.NotFound -> {
                log.debug("ETF {} not found in Alpha Vantage", etf.symbol)
                etf.avIngestionStatus = AVIngestionStatus.FAILED_PERMANENT
                etf.avIngestionErrorCode = "NOT_FOUND"
                etf.avIngestionErrorMessage = "ETF not found in Alpha Vantage"
                etfRepository.save(etf)
                false
            }

            is AVApiResult.RateLimited -> {
                log.warn("Rate limited while ingesting ETF {}", etf.symbol)
                etf.avIngestionStatus = AVIngestionStatus.FAILED_RETRYABLE
                etf.avIngestionRetryCount = etf.avIngestionRetryCount + 1
                etf.avIngestionErrorCode = "RATE_LIMITED"
                etf.avIngestionErrorMessage = result.message
                etfRepository.save(etf)
                false
            }

            is AVApiResult.QuotaExhausted -> {
                log.warn("Daily quota exhausted while ingesting ETF {}", etf.symbol)
                etf.avIngestionErrorCode = "QUOTA_EXHAUSTED"
                etf.avIngestionErrorMessage = result.message
                etfRepository.save(etf)
                false
            }

            is AVApiResult.Error -> {
                val isRetryable = avClient.isRetryableError(result)
                etf.avIngestionErrorCode = result.statusCode?.toString() ?: "UNKNOWN"
                etf.avIngestionErrorMessage = result.exception.message?.take(500)

                if (isRetryable) {
                    etf.avIngestionStatus = AVIngestionStatus.FAILED_RETRYABLE
                    etf.avIngestionRetryCount = etf.avIngestionRetryCount + 1
                } else {
                    etf.avIngestionStatus = AVIngestionStatus.FAILED_PERMANENT
                }

                etfRepository.save(etf)

                trackingService.logError(
                    stepId = step.id,
                    errorType = ErrorType.API_ERROR,
                    message = result.exception.message ?: "Unknown API error",
                    context = mapOf<String, Any>(
                        "symbol" to etf.symbol,
                        "statusCode" to (result.statusCode?.toString() ?: "unknown")
                    )
                )
                false
            }
        }
    }

    private fun handleIngestionError(etf: Etf, e: Exception, step: IngestionStep) {
        etf.avIngestionStatus = AVIngestionStatus.FAILED_RETRYABLE
        etf.avIngestionRetryCount = etf.avIngestionRetryCount + 1
        etf.avIngestionErrorCode = "UNKNOWN"
        etf.avIngestionErrorMessage = e.message?.take(500)
        etfRepository.save(etf)

        trackingService.logError(
            stepId = step.id,
            errorType = ErrorType.API_ERROR,
            message = e.message ?: "Unknown error",
            context = mapOf<String, Any>("symbol" to etf.symbol)
        )
    }
}
