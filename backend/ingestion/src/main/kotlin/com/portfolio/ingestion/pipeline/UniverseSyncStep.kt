package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.persistence.entity.*
import com.portfolio.ingestion.persistence.repository.ExchangeRepository
import com.portfolio.ingestion.persistence.repository.InstrumentExchangeRepository
import com.portfolio.ingestion.persistence.repository.InstrumentRepository
import com.portfolio.ingestion.provider.ProviderCapability
import com.portfolio.ingestion.provider.ProviderRegistry
import com.portfolio.ingestion.provider.RawInstrument
import com.portfolio.ingestion.tracking.IngestionTrackingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class UniverseSyncStep(
    private val providerRegistry: ProviderRegistry,
    private val instrumentRepo: InstrumentRepository,
    private val instrumentExchangeRepo: InstrumentExchangeRepository,
    private val exchangeRepo: ExchangeRepository,
    private val tracking: IngestionTrackingService,
    private val props: IngestionProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(val processed: Int, val created: Int, val updated: Int, val failed: Int)

    suspend fun execute(step: IngestionStep): Result {
        val provider = providerRegistry.getProvidersWithCapability(ProviderCapability.UNIVERSE).firstOrNull()
            ?: throw IllegalStateException("No provider with UNIVERSE capability")

        var totalProcessed = 0
        var totalCreated = 0
        var totalUpdated = 0
        var totalFailed = 0

        for (exchangeCode in props.targetExchanges) {
            val exchange = exchangeRepo.findByCode(exchangeCode)
            if (exchange == null) {
                log.warn("Exchange {} not found in database, skipping", exchangeCode)
                tracking.logError(step, ErrorType.VALIDATION_ERROR, errorMessage = "Exchange $exchangeCode not found")
                continue
            }

            try {
                val symbols = provider.fetchUniverse(exchangeCode)
                log.info("Fetched {} symbols for exchange {}", symbols.size, exchangeCode)

                for (raw in symbols) {
                    totalProcessed++
                    try {
                        val result = upsertInstrument(raw, exchange)
                        when (result) {
                            UpsertResult.CREATED -> totalCreated++
                            UpsertResult.UPDATED -> totalUpdated++
                        }
                    } catch (e: Exception) {
                        totalFailed++
                        tracking.logError(step, ErrorType.DB_ERROR, errorMessage = "Failed to upsert ${raw.ticker}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                tracking.logError(step, ErrorType.API_ERROR, errorMessage = "Failed to fetch universe for $exchangeCode: ${e.message}")
            }
        }

        log.info("Universe sync: processed={}, created={}, updated={}, failed={}", totalProcessed, totalCreated, totalUpdated, totalFailed)
        return Result(totalProcessed, totalCreated, totalUpdated, totalFailed)
    }

    private fun upsertInstrument(raw: RawInstrument, exchange: Exchange): UpsertResult {
        val instrumentType = mapType(raw.type)
        val now = OffsetDateTime.now()

        // Try to find by ISIN first (globally unique), then by ticker + type
        val existing = raw.isin?.let { instrumentRepo.findByIsin(it) }
            ?: instrumentRepo.findByTickerAndInstrumentType(raw.ticker, instrumentType)

        val instrument = if (existing != null) {
            existing.name = raw.name
            existing.sourceLastSeenAt = now
            existing.updatedAt = now
            instrumentRepo.save(existing)
        } else {
            instrumentRepo.save(Instrument(
                ticker = raw.ticker,
                name = raw.name,
                instrumentType = instrumentType,
                isin = raw.isin,
                currency = raw.currency,
                country = raw.country,
                sourceLastSeenAt = now
            ))
        }

        // Link to exchange (many-to-many)
        val link = instrumentExchangeRepo.findByInstrumentIdAndExchangeId(instrument.id, exchange.id)
        if (link == null) {
            instrumentExchangeRepo.save(InstrumentExchange(
                instrument = instrument,
                exchange = exchange,
                localTicker = raw.ticker,
                isPrimary = true
            ))
        }

        return if (existing != null) UpsertResult.UPDATED else UpsertResult.CREATED
    }

    private fun mapType(rawType: String): InstrumentType = when (rawType.lowercase()) {
        "common stock", "stock" -> InstrumentType.STOCK
        "preferred stock" -> InstrumentType.PREFERRED_STOCK
        "etf" -> InstrumentType.ETF
        "fund", "mutual fund" -> InstrumentType.MUTUAL_FUND
        "index" -> InstrumentType.INDEX
        "bond" -> InstrumentType.BOND
        else -> InstrumentType.STOCK
    }

    private enum class UpsertResult { CREATED, UPDATED }
}
