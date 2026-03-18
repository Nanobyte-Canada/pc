package com.portfolio.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.Stock
import com.portfolio.ingestion.client.EodhdClient
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.dto.eodhd.EodhdInstrumentDto
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.repository.StockRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class EodhdIngestionService(
    private val eodhdClient: EodhdClient,
    private val stockRepository: StockRepository,
    private val trackingService: IngestionTrackingService,
    private val config: IngestionConfig,
    private val objectMapper: ObjectMapper,
    private val entityManager: EntityManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun refreshUniverse(step: IngestionStep): StepResult {
        val now = OffsetDateTime.now()
        var totalProcessed = 0
        var totalCreated = 0
        var totalFailed = 0
        val exchangeStats = mutableMapOf<String, Int>()

        val exchanges = config.exchanges.northAmerica
        log.info("Starting universe refresh for ${exchanges.size} exchanges: $exchanges")

        for (exchange in exchanges) {
            try {
                log.info("Fetching instruments for exchange: $exchange")
                val instruments = eodhdClient.getExchangeSymbols(exchange)
                log.info("Received ${instruments.size} instruments from $exchange")

                var exchangeCreated = 0

                for (instrument in instruments) {
                    try {
                        val created = processInstrument(instrument, exchange, now, step)
                        totalProcessed++
                        if (created) {
                            totalCreated++
                            exchangeCreated++
                        }
                    } catch (e: Exception) {
                        log.error("Error processing instrument ${instrument.code}: ${e.message}")
                        entityManager.clear()
                        trackingService.logParseError(step.id, "${instrument.code}.$exchange", e)
                        totalFailed++
                    }
                }

                exchangeStats[exchange] = instruments.size
                log.info("Exchange $exchange: processed=${instruments.size}, created=$exchangeCreated")

            } catch (e: Exception) {
                log.error("Error fetching instruments for exchange $exchange: ${e.message}")
                entityManager.clear()
                trackingService.logError(
                    step.id, ErrorType.API_ERROR,
                    "Failed to fetch exchange $exchange: ${e.message}",
                    mapOf("exchange" to exchange)
                )
            }
        }

        return StepResult(
            processed = totalProcessed,
            created = totalCreated,
            updated = 0,
            failed = totalFailed,
            metadata = mapOf("exchanges" to exchangeStats)
        )
    }

    /** Returns true if a new stock was created, false otherwise. */
    private fun processInstrument(dto: EodhdInstrumentDto, exchange: String, seenAt: OffsetDateTime, step: IngestionStep): Boolean {
        if (!dto.isStock()) return false  // skip ETFs, funds, and everything else
        return processStock(dto, exchange, seenAt, step)
    }

    private fun processStock(dto: EodhdInstrumentDto, exchange: String, seenAt: OffsetDateTime, step: IngestionStep): Boolean {
        val ticker = dto.code
        val isin = dto.isin

        // Skip if stock already exists — insert-only semantics
        if (stockRepository.findByTickerIgnoreCase(ticker) != null) return false

        // ISIN conflict check for new records
        if (!isin.isNullOrBlank()) {
            val existingByIsin = stockRepository.findByIsin(isin)
            if (existingByIsin != null) {
                val newIdentifier = "$ticker.$exchange"
                val existingIdentifier = existingByIsin.ticker
                log.warn("ISIN conflict: $isin already belongs to $existingIdentifier, skipping $newIdentifier")
                trackingService.logDuplicateIsin(step.id, newIdentifier, existingIdentifier, isin)
                return false
            }
        }

        stockRepository.save(Stock(
            ticker   = ticker,
            name     = dto.name ?: ticker,
            currency = (dto.currency ?: "USD").take(3),
            country  = (dto.country  ?: "USA").take(3),
            isin     = isin
        ))
        return true
    }
}
