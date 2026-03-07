package com.portfolio.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.Etf
import com.portfolio.entity.MutualFund
import com.portfolio.entity.Stock
import com.portfolio.ingestion.client.EodhdClient
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.dto.eodhd.EodhdInstrumentDto
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.repository.EtfRepository
import jakarta.persistence.EntityManager
import com.portfolio.repository.MutualFundRepository
import com.portfolio.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class EodhdIngestionService(
    private val eodhdClient: EodhdClient,
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository,
    private val mutualFundRepository: MutualFundRepository,
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
        var totalUpdated = 0
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
                var exchangeUpdated = 0

                for (instrument in instruments) {
                    try {
                        val result = processInstrument(instrument, exchange, now, step)
                        totalProcessed++
                        if (result.created) {
                            totalCreated++
                            exchangeCreated++
                        } else if (result.updated) {
                            totalUpdated++
                            exchangeUpdated++
                        }
                    } catch (e: Exception) {
                        log.error("Error processing instrument ${instrument.code}: ${e.message}")
                        entityManager.clear()  // Reset session state after exception
                        trackingService.logParseError(step.id, "${instrument.code}.$exchange", e)
                        totalFailed++
                    }
                }

                exchangeStats[exchange] = instruments.size
                log.info("Exchange $exchange: processed=${instruments.size}, created=$exchangeCreated, updated=$exchangeUpdated")

            } catch (e: Exception) {
                log.error("Error fetching instruments for exchange $exchange: ${e.message}")
                entityManager.clear()  // Reset session state after exception
                trackingService.logError(
                    step.id, ErrorType.API_ERROR,
                    "Failed to fetch exchange $exchange: ${e.message}",
                    mapOf("exchange" to exchange)
                )
            }
        }

        // Mark stale instruments
        val staleCount = markStaleInstruments(now)
        log.info("Marked $staleCount instruments as stale")

        return StepResult(
            processed = totalProcessed,
            created = totalCreated,
            updated = totalUpdated,
            failed = totalFailed,
            metadata = mapOf(
                "exchanges" to exchangeStats,
                "staleCount" to staleCount
            )
        )
    }

    private data class ProcessResult(val created: Boolean, val updated: Boolean)

    private fun processInstrument(dto: EodhdInstrumentDto, exchange: String, seenAt: OffsetDateTime, step: IngestionStep): ProcessResult {
        val rawPayload = objectMapper.writeValueAsString(dto)

        return when {
            dto.isStock() -> processStock(dto, exchange, seenAt, rawPayload, step)
            dto.isEtf() -> {
                // Skip ETF processing when etf.com is enabled (etf.com handles ETF universe)
                if (config.etfcom.enabled) {
                    ProcessResult(created = false, updated = false)
                } else {
                    processEtf(dto, exchange, seenAt, rawPayload, step)
                }
            }
            dto.isMutualFund() -> processMutualFund(dto, seenAt, rawPayload, step)
            else -> ProcessResult(created = false, updated = false)
        }
    }

    private fun processStock(dto: EodhdInstrumentDto, exchange: String, seenAt: OffsetDateTime, rawPayload: String, step: IngestionStep): ProcessResult {
        val ticker = dto.code
        val isin = dto.isin

        // First, check for existing record by ticker+exchange
        val existingByTicker = stockRepository.findByTickerAndExchange(ticker, exchange)

        if (existingByTicker != null) {
            // Update existing record - ISIN change is allowed on same record
            existingByTicker.apply {
                name = dto.name ?: name
                currency = (dto.currency ?: currency).take(3)
                country = (dto.country ?: country).take(3)
                exchangeCode = dto.exchange
                this.isin = isin ?: this.isin
                isActive = true
                sourceLastSeenAt = seenAt
                this.rawEodhdPayload = rawPayload
                updatedAt = OffsetDateTime.now()
            }
            stockRepository.save(existingByTicker)
            return ProcessResult(created = false, updated = true)
        }

        // New record - check if ISIN already exists on a different stock
        if (!isin.isNullOrBlank()) {
            val existingByIsin = stockRepository.findByIsin(isin)
            if (existingByIsin != null) {
                // ISIN conflict detected - log warning and skip
                val newIdentifier = "$ticker.$exchange"
                val existingIdentifier = "${existingByIsin.ticker}.${existingByIsin.exchange}"
                log.warn("ISIN conflict: $isin already belongs to $existingIdentifier, skipping $newIdentifier")
                trackingService.logDuplicateIsin(step.id, newIdentifier, existingIdentifier, isin)
                return ProcessResult(created = false, updated = false)
            }
        }

        // Safe to insert new record
        stockRepository.save(Stock(
            ticker = ticker,
            exchange = exchange,
            name = dto.name ?: ticker,
            currency = (dto.currency ?: "USD").take(3),
            country = (dto.country ?: "USA").take(3),
            exchangeCode = dto.exchange,
            isin = isin,
            isActive = true,
            sourceLastSeenAt = seenAt,
            rawEodhdPayload = rawPayload
        ))
        return ProcessResult(created = true, updated = false)
    }

    private fun processEtf(dto: EodhdInstrumentDto, exchange: String, seenAt: OffsetDateTime, rawPayload: String, step: IngestionStep): ProcessResult {
        val symbol = dto.code
        val isin = dto.isin

        // Check for existing record by symbol
        val existingBySymbol = etfRepository.findBySymbolIgnoreCase(symbol)

        if (existingBySymbol != null) {
            existingBySymbol.apply {
                name = dto.name ?: name
                currency = (dto.currency ?: currency).take(3)
                this.isin = isin ?: this.isin
                isActive = true
                sourceLastSeenAt = seenAt
                updatedAt = OffsetDateTime.now()
            }
            etfRepository.save(existingBySymbol)
            return ProcessResult(created = false, updated = true)
        }

        // New record - check if ISIN already exists on a different ETF
        if (!isin.isNullOrBlank()) {
            val existingByIsin = etfRepository.findByIsin(isin)
            if (existingByIsin != null) {
                val newIdentifier = "$symbol.$exchange"
                val existingIdentifier = existingByIsin.symbol
                log.warn("ISIN conflict: $isin already belongs to ETF $existingIdentifier, skipping $newIdentifier")
                trackingService.logDuplicateIsin(step.id, newIdentifier, existingIdentifier, isin)
                return ProcessResult(created = false, updated = false)
            }
        }

        etfRepository.save(Etf(
            symbol = symbol,
            name = dto.name ?: symbol,
            currency = (dto.currency ?: "USD").take(3),
            domicile = (dto.country ?: "USA").take(3),
            isin = isin,
            isActive = true,
            sourceLastSeenAt = seenAt
        ))
        return ProcessResult(created = true, updated = false)
    }

    private fun processMutualFund(dto: EodhdInstrumentDto, seenAt: OffsetDateTime, rawPayload: String, step: IngestionStep): ProcessResult {
        val symbol = dto.code
        val isin = dto.isin

        // Check for existing record by symbol
        val existingBySymbol = mutualFundRepository.findBySymbol(symbol)

        if (existingBySymbol != null) {
            existingBySymbol.apply {
                name = dto.name ?: name
                currency = (dto.currency ?: currency).take(3)
                this.isin = isin ?: this.isin
                isActive = true
                sourceLastSeenAt = seenAt
                this.rawEodhdPayload = rawPayload
                updatedAt = OffsetDateTime.now()
            }
            mutualFundRepository.save(existingBySymbol)
            return ProcessResult(created = false, updated = true)
        }

        // New record - check if ISIN already exists on a different Mutual Fund
        if (!isin.isNullOrBlank()) {
            val existingByIsin = mutualFundRepository.findByIsin(isin)
            if (existingByIsin != null) {
                val newIdentifier = symbol
                val existingIdentifier = existingByIsin.symbol
                log.warn("ISIN conflict: $isin already belongs to MutualFund $existingIdentifier, skipping $newIdentifier")
                trackingService.logDuplicateIsin(step.id, newIdentifier, existingIdentifier, isin)
                return ProcessResult(created = false, updated = false)
            }
        }

        mutualFundRepository.save(MutualFund(
            symbol = symbol,
            name = dto.name ?: symbol,
            currency = (dto.currency ?: "USD").take(3),
            domicile = (dto.country ?: "USA").take(3),
            isin = isin,
            isActive = true,
            sourceLastSeenAt = seenAt,
            rawEodhdPayload = rawPayload
        ))
        return ProcessResult(created = true, updated = false)
    }

    private fun markStaleInstruments(currentRunTime: OffsetDateTime): Int {
        // Mark instruments as inactive if they weren't seen in this run
        // Using a small buffer (1 minute) to account for timing
        val cutoff = currentRunTime.minusMinutes(1)

        var staleCount = 0

        // Mark stale stocks
        val staleStocks = stockRepository.findStaleStocks(cutoff)
        for (stock in staleStocks) {
            stock.isActive = false
            stock.updatedAt = OffsetDateTime.now()
        }
        stockRepository.saveAll(staleStocks)
        staleCount += staleStocks.size

        // Mark stale ETFs (skip when etf.com handles ETF universe)
        if (!config.etfcom.enabled) {
            val staleEtfs = etfRepository.findStaleEtfs(cutoff)
            for (etf in staleEtfs) {
                etf.isActive = false
                etf.updatedAt = OffsetDateTime.now()
            }
            etfRepository.saveAll(staleEtfs)
            staleCount += staleEtfs.size
        }

        // Mark stale Mutual Funds
        val staleMutualFunds = mutualFundRepository.findStaleMutualFunds(cutoff)
        for (mf in staleMutualFunds) {
            mf.isActive = false
            mf.updatedAt = OffsetDateTime.now()
        }
        mutualFundRepository.saveAll(staleMutualFunds)
        staleCount += staleMutualFunds.size

        return staleCount
    }
}
