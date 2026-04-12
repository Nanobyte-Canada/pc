package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.persistence.entity.Exchange
import com.portfolio.ingestion.persistence.entity.ErrorType
import com.portfolio.ingestion.persistence.entity.IngestionStep
import com.portfolio.ingestion.persistence.repository.ExchangeRepository
import com.portfolio.ingestion.provider.ProviderCapability
import com.portfolio.ingestion.provider.ProviderRegistry
import com.portfolio.ingestion.tracking.IngestionTrackingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ExchangeSyncStep(
    private val providerRegistry: ProviderRegistry,
    private val exchangeRepo: ExchangeRepository,
    private val tracking: IngestionTrackingService,
    private val props: com.portfolio.ingestion.config.IngestionProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(val processed: Int, val created: Int, val updated: Int, val failed: Int)

    suspend fun execute(step: IngestionStep): Result {
        val provider = providerRegistry.getProvidersWithCapability(ProviderCapability.EXCHANGES).firstOrNull()
            ?: throw IllegalStateException("No provider with EXCHANGES capability")

        var processed = 0
        var created = 0
        var updated = 0
        var failed = 0

        try {
            val exchanges = provider.fetchExchanges()
            for (raw in exchanges) {
                processed++
                try {
                    val existing = exchangeRepo.findByCode(raw.code)
                    if (existing != null) {
                        existing.name = raw.name
                        existing.country = raw.country
                        existing.currency = raw.currency
                        existing.operatingMic = raw.operatingMic
                        exchangeRepo.save(existing)
                        updated++
                    } else {
                        exchangeRepo.save(Exchange(
                            code = raw.code,
                            name = raw.name,
                            country = raw.country,
                            currency = raw.currency,
                            operatingMic = raw.operatingMic
                        ))
                        created++
                    }
                } catch (e: Exception) {
                    failed++
                    tracking.logError(step, ErrorType.DB_ERROR, errorMessage = "Failed to save exchange ${raw.code}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            tracking.logError(step, ErrorType.API_ERROR, errorMessage = "Failed to fetch exchanges: ${e.message}")
        }

        // Ensure all target exchanges exist (some like INDX aren't in EODHD's exchange list)
        for (code in props.targetExchanges) {
            if (exchangeRepo.findByCode(code) == null) {
                exchangeRepo.save(Exchange(code = code, name = "$code (virtual)"))
                created++
                log.info("Pre-seeded missing target exchange: {}", code)
            }
        }

        log.info("Exchange sync: processed={}, created={}, updated={}, failed={}", processed, created, updated, failed)
        return Result(processed, created, updated, failed)
    }
}
