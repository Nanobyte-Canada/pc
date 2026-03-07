package com.portfolio.ingestion.service.etfcom

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.*
import com.portfolio.ingestion.client.etfcom.EtfComApiResult
import com.portfolio.ingestion.client.etfcom.EtfComClient
import com.portfolio.ingestion.dto.etfcom.EtfComEnrichmentData
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.BatchResult
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.EtfHoldingRepository
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.EtfSectorAllocationFactsetRepository
import com.portfolio.repository.StockRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class EtfComEnrichmentBatchProcessor(
    private val etfComClient: EtfComClient,
    private val etfRepository: EtfRepository,
    private val stockRepository: StockRepository,
    private val etfHoldingRepository: EtfHoldingRepository,
    private val sectorRepository: EtfSectorAllocationFactsetRepository,
    private val trackingService: IngestionTrackingService,
    private val objectMapper: ObjectMapper,
    private val entityManager: EntityManager,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val enrichmentSuccess = Counter.builder("etfcom_enrichment_total")
        .tag("status", "success")
        .register(meterRegistry)

    private val enrichmentFailed = Counter.builder("etfcom_enrichment_total")
        .tag("status", "failed")
        .register(meterRegistry)

    private val holdingsCreated = Counter.builder("etfcom_holdings_created_total")
        .register(meterRegistry)

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
                entityManager.flush()
            } catch (e: Exception) {
                log.error("Unexpected error enriching ETF {} from etf.com: {}", etf.symbol, e.message, e)
                entityManager.clear()

                val freshEtf = etfRepository.findById(etf.id).orElse(null)
                if (freshEtf != null) {
                    handleEnrichmentError(freshEtf, e, step)
                }

                failed++
                enrichmentFailed.increment()
            }
        }

        return BatchResult(updated = updated, failed = failed, processed = batch.size)
    }

    private fun enrichEtf(etf: Etf, step: IngestionStep): Boolean {
        etf.etfcomLastAttemptAt = OffsetDateTime.now()

        val result = etfComClient.fetchAllData(etf.symbol, etf.etfcomFundId)

        return when (result) {
            is EtfComApiResult.NotFound -> {
                log.debug("ETF {} not found on etf.com", etf.symbol)
                etf.etfcomEnrichmentStatus = EtfComEnrichmentStatus.FAILED_PERMANENT
                etf.etfcomErrorCode = "NOT_FOUND"
                etf.etfcomErrorMessage = "Ticker not found on etf.com"
                etfRepository.save(etf)
                false
            }

            is EtfComApiResult.Error -> {
                log.warn("Error enriching ETF {} from etf.com: {}", etf.symbol, result.message)
                etf.etfcomEnrichmentStatus = EtfComEnrichmentStatus.FAILED_RETRYABLE
                etf.etfcomRetryCount++
                etf.etfcomErrorCode = result.statusCode?.toString() ?: "UNKNOWN"
                etf.etfcomErrorMessage = result.message.take(500)
                etfRepository.save(etf)

                trackingService.logError(
                    stepId = step.id,
                    errorType = ErrorType.API_ERROR,
                    message = "etf.com API error for ${etf.symbol}: ${result.message}",
                    context = mapOf("symbol" to etf.symbol, "statusCode" to (result.statusCode ?: "unknown"))
                )
                false
            }

            is EtfComApiResult.Success -> {
                val data = result.data

                // Store combined raw JSON
                etf.etfcomRawPayload = objectMapper.writeValueAsString(data.rawPayloads)

                // Map summary fields
                mapSummaryToEtf(etf, data)

                // Map portfolio fields
                mapPortfolioToEtf(etf, data)

                // Map performance fields
                mapPerformanceToEtf(etf, data)

                // Process holdings
                processHoldings(etf, data, step)

                // Process sectors
                processSectors(etf, data)

                // Update top-level Etf fields from etf.com data
                data.summary?.let { summary ->
                    summary.issuer?.let { etf.issuer = it }
                    summary.expenseRatio?.let { etf.expenseRatio = it }
                    summary.inceptionDate?.let { etf.inceptionDate = parseDate(it) ?: etf.inceptionDate }
                }

                etf.etfcomEnrichmentStatus = EtfComEnrichmentStatus.SUCCESS
                etf.etfcomLastSuccessAt = OffsetDateTime.now()
                etf.etfcomRetryCount = 0
                etf.etfcomErrorCode = null
                etf.etfcomErrorMessage = null
                etf.updatedAt = OffsetDateTime.now()
                etfRepository.save(etf)

                log.debug("Successfully enriched ETF {} from etf.com", etf.symbol)
                true
            }
        }
    }

    private fun mapSummaryToEtf(etf: Etf, data: EtfComEnrichmentData) {
        data.summary?.let { summary ->
            etf.etfcomIssuer = summary.issuer
            etf.etfcomInceptionDate = parseDate(summary.inceptionDate)
            etf.etfcomExpenseRatio = summary.expenseRatio
            etf.etfcomAum = summary.aum
            etf.etfcomIndexTracked = summary.indexTracked
            etf.etfcomSegment = summary.segment
            etf.etfcomDescription = summary.description
        }
    }

    private fun mapPortfolioToEtf(etf: Etf, data: EtfComEnrichmentData) {
        data.portfolio?.let { portfolio ->
            etf.etfcomWeightedAvgMarketCap = portfolio.weightedAvgMarketCap
            etf.etfcomPeRatio = portfolio.priceToEarnings
            etf.etfcomPbRatio = portfolio.priceToBook
            etf.etfcomDividendYield = portfolio.dividendYield
        }
    }

    private fun mapPerformanceToEtf(etf: Etf, data: EtfComEnrichmentData) {
        data.performance?.let { perf ->
            perf.nav?.let { nav ->
                etf.etfcomReturn1mNav = nav.oneMonth
                etf.etfcomReturn3mNav = nav.threeMonth
                etf.etfcomReturnYtdNav = nav.ytd
                etf.etfcomReturn1yNav = nav.oneYear
                etf.etfcomReturn3yNav = nav.threeYear
                etf.etfcomReturn5yNav = nav.fiveYear
            }
            perf.price?.let { price ->
                etf.etfcomReturn1mPrice = price.oneMonth
                etf.etfcomReturn3mPrice = price.threeMonth
                etf.etfcomReturnYtdPrice = price.ytd
                etf.etfcomReturn1yPrice = price.oneYear
                etf.etfcomReturn3yPrice = price.threeYear
                etf.etfcomReturn5yPrice = price.fiveYear
            }
            etf.etfcomPerformanceAsOfDate = parseDate(perf.asOfDate)
        }
    }

    private fun processHoldings(etf: Etf, data: EtfComEnrichmentData, step: IngestionStep) {
        val holdingsData = data.holdings ?: return
        val holdings = holdingsData.holdings ?: return
        if (holdings.isEmpty()) return

        val asOfDate = parseDate(holdingsData.asOfDate) ?: LocalDate.now()
        etf.etfcomHoldingsCount = holdingsData.numberOfHoldings ?: holdings.size
        etf.etfcomHoldingsAsOfDate = asOfDate

        // Delete existing holdings for this ETF before re-inserting
        etfHoldingRepository.deleteByEtfId(etf.id)

        var holdingsProcessed = 0

        for ((rank, holding) in holdings.withIndex()) {
            val symbol = holding.symbol ?: continue
            val weight = holding.weight ?: continue

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

            val newHolding = EtfHolding(
                etf = etf,
                stock = stock,
                asOfDate = asOfDate,
                weight = weight,
                rawTicker = symbol,
                rawName = holding.holdingName,
                resolutionStatus = if (stock != null) ResolutionStatus.RESOLVED else ResolutionStatus.UNRESOLVED,
                holdingType = HoldingType.STOCK,
                rank = rank + 1,
                dataSource = HoldingDataSource.ETF_COM,
                etfcomWeight = weight,
                etfcomLastUpdatedAt = OffsetDateTime.now()
            )
            etfHoldingRepository.save(newHolding)
            holdingsCreated.increment()
            holdingsProcessed++
        }

        log.debug("Processed {} holdings for ETF {}", holdingsProcessed, etf.symbol)
    }

    private fun processSectors(etf: Etf, data: EtfComEnrichmentData) {
        val sectorData = data.sectors ?: return
        val sectors = sectorData.sectors ?: return
        if (sectors.isEmpty()) return

        val asOfDate = LocalDate.now()

        // Delete old sector allocations for this date
        sectorRepository.deleteByEtfIdAndAsOfDate(etf.id, asOfDate)

        for (sector in sectors) {
            val sectorName = sector.sectorName ?: continue
            sectorRepository.save(
                EtfSectorAllocationFactset(
                    etf = etf,
                    sectorName = sectorName,
                    weight = sector.weight,
                    asOfDate = asOfDate
                )
            )
        }

        log.debug("Processed {} sectors for ETF {}", sectors.size, etf.symbol)
    }

    private fun handleEnrichmentError(etf: Etf, e: Exception, step: IngestionStep) {
        etf.etfcomEnrichmentStatus = EtfComEnrichmentStatus.FAILED_RETRYABLE
        etf.etfcomRetryCount++
        etf.etfcomErrorCode = "UNKNOWN"
        etf.etfcomErrorMessage = e.message?.take(500)
        etfRepository.save(etf)

        trackingService.logError(
            stepId = step.id,
            errorType = ErrorType.PARSE_ERROR,
            message = e.message ?: "Unknown error",
            context = mapOf<String, Any>("symbol" to etf.symbol)
        )
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank() || dateStr == "None" || dateStr == "-") return null
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
}
