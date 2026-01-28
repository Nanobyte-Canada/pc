package com.portfolio.ingestion.service.alphavantage

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.AVEnrichmentStatus
import com.portfolio.entity.Etf
import com.portfolio.entity.EtfHolding
import com.portfolio.entity.HoldingDataSource
import com.portfolio.entity.HoldingType
import com.portfolio.entity.ResolutionStatus
import com.portfolio.ingestion.dto.alphavantage.AVEtfProfileResponse
import com.portfolio.ingestion.dto.alphavantage.SectorAllocations
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.BatchResult
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.EtfHoldingRepository
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.StockRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Batch processor for ETF enrichment with per-batch transaction commits.
 *
 * Uses REQUIRES_NEW propagation so each batch is committed independently.
 * This ensures that if a batch fails, previously processed batches remain committed.
 */
@Service
class AVEtfEnrichmentBatchProcessor(
    private val etfRepository: EtfRepository,
    private val stockRepository: StockRepository,
    private val etfHoldingRepository: EtfHoldingRepository,
    private val trackingService: IngestionTrackingService,
    private val objectMapper: ObjectMapper,
    private val entityManager: EntityManager,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val enrichmentSuccess = Counter.builder("av_enrichment_total")
        .tag("type", "etf")
        .tag("status", "success")
        .register(meterRegistry)

    private val enrichmentFailed = Counter.builder("av_enrichment_total")
        .tag("type", "etf")
        .tag("status", "failed")
        .register(meterRegistry)

    private val holdingsCreated = Counter.builder("av_etf_holdings_created_total")
        .register(meterRegistry)

    /**
     * Processes a batch of ETFs with an independent transaction.
     *
     * Each call to this method runs in its own transaction (REQUIRES_NEW),
     * so it will commit when the method returns, regardless of what happens
     * in subsequent batches.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processBatch(batch: List<Etf>, step: IngestionStep): BatchResult {
        var updated = 0
        var failed = 0

        for (etf in batch) {
            try {
                val enriched = enrichEtf(etf, step)
                if (enriched) {
                    updated++
                    enrichmentSuccess.increment()
                } else {
                    failed++
                    enrichmentFailed.increment()
                }
                // Flush after each entity to persist changes immediately within this transaction
                entityManager.flush()
            } catch (e: Exception) {
                log.error("Unexpected error enriching ETF {}: {}", etf.symbol, e.message, e)

                // Clear the session to reset corrupted state
                entityManager.clear()

                // Re-fetch the ETF in clean session and update error state
                val freshEtf = etfRepository.findById(etf.id).orElse(null)
                if (freshEtf != null) {
                    handleEnrichmentError(freshEtf, e, step)
                }

                failed++
                enrichmentFailed.increment()
            }
        }

        // Commits when this method returns due to REQUIRES_NEW
        return BatchResult(updated = updated, failed = failed, processed = batch.size)
    }

    /**
     * Enriches a single ETF from its stored raw payload.
     */
    private fun enrichEtf(etf: Etf, step: IngestionStep): Boolean {
        etf.avLastAttemptAt = OffsetDateTime.now()

        val rawPayload = etf.avRawPayload
        if (rawPayload.isNullOrBlank()) {
            log.warn("ETF {} has no raw payload to enrich from", etf.symbol)
            etf.avEnrichmentStatus = AVEnrichmentStatus.FAILED_PERMANENT
            etf.avErrorCode = "NO_PAYLOAD"
            etf.avErrorMessage = "No raw payload available for enrichment"
            etfRepository.save(etf)
            return false
        }

        val response = parseRawPayload(rawPayload)
        if (response == null) {
            log.warn("Failed to parse raw payload for ETF {}", etf.symbol)
            etf.avEnrichmentStatus = AVEnrichmentStatus.FAILED_PERMANENT
            etf.avErrorCode = "PARSE_ERROR"
            etf.avErrorMessage = "Failed to parse stored raw payload"
            etfRepository.save(etf)

            trackingService.logError(
                stepId = step.id,
                errorType = ErrorType.PARSE_ERROR,
                message = "Failed to parse stored raw payload",
                context = mapOf<String, Any>("symbol" to etf.symbol)
            )
            return false
        }

        mapResponseToEtf(etf, response)
        processHoldings(etf, response, step)

        etf.avEnrichmentStatus = AVEnrichmentStatus.SUCCESS
        etf.avLastSuccessAt = OffsetDateTime.now()
        etf.avRetryCount = 0
        etf.avErrorCode = null
        etf.avErrorMessage = null
        etfRepository.save(etf)
        log.debug("Successfully enriched ETF {} from stored payload", etf.symbol)
        return true
    }

    private fun parseRawPayload(rawJson: String): AVEtfProfileResponse? {
        return try {
            objectMapper.readValue(rawJson, AVEtfProfileResponse::class.java)
        } catch (e: Exception) {
            log.error("Failed to parse raw payload: {}", e.message)
            null
        }
    }

    private fun mapResponseToEtf(etf: Etf, response: AVEtfProfileResponse) {
        etf.avAssetType = response.assetType
        etf.avDescription = response.description
        etf.avNetAssets = response.parseDecimal(response.netAssets)
        etf.avNetExpenseRatio = response.parseDecimal(response.netExpenseRatio)
        etf.avPortfolioTurnover = response.parseDecimal(response.portfolioTurnover)
        etf.avDividendYield = response.parseDecimal(response.dividendYield)
        etf.avInceptionDate = parseDate(response.inceptionDate)
        etf.avIsLeveraged = response.isLeveraged()
        etf.avHoldingsCount = response.parseInt(response.holdingsCount)
        etf.avHoldingsAsOfDate = LocalDate.now()

        // Sector allocations
        val sectors = SectorAllocations.fromResponse(response)
        etf.avSectorInfoTech = sectors.informationTechnology
        etf.avSectorCommServices = sectors.communicationServices
        etf.avSectorConsumerDisc = sectors.consumerDiscretionary
        etf.avSectorConsumerStaples = sectors.consumerStaples
        etf.avSectorHealthcare = sectors.healthCare
        etf.avSectorIndustrials = sectors.industrials
        etf.avSectorUtilities = sectors.utilities
        etf.avSectorMaterials = sectors.materials
        etf.avSectorEnergy = sectors.energy
        etf.avSectorFinancials = sectors.financials
        etf.avSectorRealEstate = sectors.realEstate

        etf.updatedAt = OffsetDateTime.now()
    }

    private fun processHoldings(etf: Etf, response: AVEtfProfileResponse, step: IngestionStep) {
        val holdings = response.holdings ?: return

        if (holdings.isEmpty()) {
            log.debug("No holdings data in Alpha Vantage response for ETF {}", etf.symbol)
            return
        }

        val asOfDate = LocalDate.now()
        var holdingsProcessed = 0

        for ((rank, avHolding) in holdings.withIndex()) {
            val symbol = avHolding.symbol ?: continue
            val weight = avHolding.parseWeight() ?: continue

            // Try to resolve the stock
            val stock = stockRepository.findAllByTickerIgnoreCase(symbol)
                .filter { it.isActive }
                .minByOrNull {
                    when (it.exchange.uppercase()) {
                        "NYSE", "NASDAQ", "US" -> 0
                        "ARCA", "BATS" -> 1
                        else -> 2
                    }
                }

            // Check if holding already exists
            val existingHolding = if (stock != null) {
                etfHoldingRepository.findByEtfIdAndStockIdAndAsOfDate(etf.id, stock.id, asOfDate)
            } else {
                etfHoldingRepository.findByEtfIdAndRawTickerAndAsOfDate(etf.id, symbol, asOfDate)
            }

            if (existingHolding != null) {
                existingHolding.avWeight = weight
                existingHolding.avLastUpdatedAt = OffsetDateTime.now()
                if (existingHolding.dataSource != HoldingDataSource.ALPHA_VANTAGE) {
                    existingHolding.dataSource = HoldingDataSource.ALPHA_VANTAGE
                }
                etfHoldingRepository.save(existingHolding)
            } else {
                val holding = EtfHolding(
                    etf = etf,
                    stock = stock,
                    asOfDate = asOfDate,
                    weight = weight,
                    rawTicker = symbol,
                    rawName = avHolding.name ?: avHolding.description,
                    resolutionStatus = if (stock != null) ResolutionStatus.RESOLVED else ResolutionStatus.UNRESOLVED,
                    holdingType = HoldingType.STOCK,
                    rank = rank + 1,
                    dataSource = HoldingDataSource.ALPHA_VANTAGE,
                    avWeight = weight,
                    avLastUpdatedAt = OffsetDateTime.now()
                )
                etfHoldingRepository.save(holding)
                holdingsCreated.increment()
            }
            holdingsProcessed++
        }

        log.debug("Processed {} holdings for ETF {}", holdingsProcessed, etf.symbol)
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank() || dateStr == "None" || dateStr == "-") {
            return null
        }
        return try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            try {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun handleEnrichmentError(etf: Etf, e: Exception, step: IngestionStep) {
        etf.avEnrichmentStatus = AVEnrichmentStatus.FAILED_RETRYABLE
        etf.avRetryCount = (etf.avRetryCount) + 1
        etf.avErrorCode = "UNKNOWN"
        etf.avErrorMessage = e.message?.take(500)
        etfRepository.save(etf)

        trackingService.logError(
            stepId = step.id,
            errorType = ErrorType.PARSE_ERROR,
            message = e.message ?: "Unknown error",
            context = mapOf<String, Any>("symbol" to etf.symbol)
        )
    }
}
