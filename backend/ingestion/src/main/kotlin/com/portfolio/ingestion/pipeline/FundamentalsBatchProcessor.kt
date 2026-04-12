package com.portfolio.ingestion.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.persistence.entity.ErrorType
import com.portfolio.ingestion.persistence.entity.Instrument
import com.portfolio.ingestion.persistence.entity.IngestionStep
import com.portfolio.ingestion.persistence.entity.ProviderRawData
import com.portfolio.ingestion.persistence.repository.ProviderRawDataRepository
import com.portfolio.ingestion.provider.eodhd.EodhdClient
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import com.portfolio.ingestion.tracking.HashCacheService
import com.portfolio.ingestion.tracking.IngestionTrackingService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class FundamentalsBatchProcessor(
    private val client: EodhdClient,
    private val rateLimiter: EodhdRateLimiter,
    private val hashCache: HashCacheService,
    private val rawDataRepo: ProviderRawDataRepository,
    private val tracking: IngestionTrackingService,
    private val objectMapper: ObjectMapper,
    private val props: IngestionProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class InstrumentWithExchange(val instrument: Instrument, val exchangeCode: String)
    data class BatchResult(val processed: Int, val updated: Int, val skipped: Int, val failed: Int)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun processBatch(items: List<InstrumentWithExchange>, step: IngestionStep): BatchResult {
        var updated = 0
        var skipped = 0
        var failed = 0
        val cost = props.eodhd.fundamentalsCost

        coroutineScope {
            val results = items.map { (instrument, exchange) ->
                async {
                    try {
                        rateLimiter.acquire(cost)

                        val ticker = instrument.ticker

                        val payload = client.fetchFundamentals(ticker, exchange)
                        val payloadStr = objectMapper.writeValueAsString(payload)
                        val hash = hashCache.computeHash(payloadStr)
                        val cacheKey = "EODHD:FUNDAMENTALS:${instrument.id}"

                        if (hashCache.isUnchanged(cacheKey, hash)) {
                            return@async FetchResult.SKIPPED
                        }

                        // Upsert raw data
                        val existing = rawDataRepo.findByInstrumentIdAndProviderAndDataType(
                            instrument.id, "EODHD", "FUNDAMENTALS"
                        )

                        if (existing != null) {
                            existing.rawPayload = payload
                            existing.payloadHash = hash
                            existing.fetchedAt = OffsetDateTime.now()
                            rawDataRepo.save(existing)
                        } else {
                            rawDataRepo.save(ProviderRawData(
                                instrument = instrument,
                                provider = "EODHD",
                                dataType = "FUNDAMENTALS",
                                rawPayload = payload,
                                payloadHash = hash
                            ))
                        }

                        hashCache.storeHash(cacheKey, hash)
                        FetchResult.UPDATED
                    } catch (e: Exception) {
                        tracking.logError(step, ErrorType.API_ERROR,
                            errorMessage = "Failed fundamentals for ${instrument.ticker}: ${e.message}",
                            context = mapOf("instrumentId" to instrument.id, "ticker" to instrument.ticker)
                        )
                        FetchResult.FAILED
                    }
                }
            }

            for (result in results.awaitAll()) {
                when (result) {
                    FetchResult.UPDATED -> updated++
                    FetchResult.SKIPPED -> skipped++
                    FetchResult.FAILED -> failed++
                }
            }
        }

        log.info("Batch: processed={}, updated={}, skipped={}, failed={}", items.size, updated, skipped, failed)
        return BatchResult(items.size, updated, skipped, failed)
    }

    private enum class FetchResult { UPDATED, SKIPPED, FAILED }
}
