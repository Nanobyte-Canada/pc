package com.portfolio.ingestion.service.alphavantage

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.AVEnrichmentStatus
import com.portfolio.entity.Stock
import com.portfolio.ingestion.dto.alphavantage.AVOverviewResponse
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.BatchResult
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.StockRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Batch processor for stock enrichment with per-batch transaction commits.
 *
 * Uses REQUIRES_NEW propagation so each batch is committed independently.
 * This ensures that if a batch fails, previously processed batches remain committed.
 */
@Service
class AVStockEnrichmentBatchProcessor(
    private val stockRepository: StockRepository,
    private val trackingService: IngestionTrackingService,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val enrichmentSuccess = Counter.builder("av_enrichment_total")
        .tag("type", "stock")
        .tag("status", "success")
        .register(meterRegistry)

    private val enrichmentFailed = Counter.builder("av_enrichment_total")
        .tag("type", "stock")
        .tag("status", "failed")
        .register(meterRegistry)

    /**
     * Processes a batch of stocks with an independent transaction.
     *
     * Each call to this method runs in its own transaction (REQUIRES_NEW),
     * so it will commit when the method returns, regardless of what happens
     * in subsequent batches.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processBatch(batch: List<Stock>, step: IngestionStep): BatchResult {
        var updated = 0
        var failed = 0

        for (stock in batch) {
            try {
                val enriched = enrichStock(stock, step)
                if (enriched) {
                    updated++
                    enrichmentSuccess.increment()
                } else {
                    failed++
                    enrichmentFailed.increment()
                }
            } catch (e: Exception) {
                log.error("Unexpected error enriching stock {}: {}", stock.ticker, e.message)
                handleEnrichmentError(stock, e, step)
                failed++
                enrichmentFailed.increment()
            }
        }

        // Commits when this method returns due to REQUIRES_NEW
        return BatchResult(updated = updated, failed = failed, processed = batch.size)
    }

    /**
     * Enriches a single stock from its stored raw payload.
     */
    private fun enrichStock(stock: Stock, step: IngestionStep): Boolean {
        stock.avLastAttemptAt = OffsetDateTime.now()

        val rawPayload = stock.avRawPayload
        if (rawPayload.isNullOrBlank()) {
            log.warn("Stock {} has no raw payload to enrich from", stock.ticker)
            stock.avEnrichmentStatus = AVEnrichmentStatus.FAILED_PERMANENT
            stock.avErrorCode = "NO_PAYLOAD"
            stock.avErrorMessage = "No raw payload available for enrichment"
            stockRepository.save(stock)
            return false
        }

        val response = parseRawPayload(rawPayload)
        if (response == null) {
            log.warn("Failed to parse raw payload for stock {}", stock.ticker)
            stock.avEnrichmentStatus = AVEnrichmentStatus.FAILED_PERMANENT
            stock.avErrorCode = "PARSE_ERROR"
            stock.avErrorMessage = "Failed to parse stored raw payload"
            stockRepository.save(stock)

            trackingService.logError(
                stepId = step.id,
                errorType = ErrorType.PARSE_ERROR,
                message = "Failed to parse stored raw payload",
                context = mapOf<String, Any>("ticker" to stock.ticker)
            )
            return false
        }

        mapResponseToStock(stock, response)
        stock.avEnrichmentStatus = AVEnrichmentStatus.SUCCESS
        stock.avLastSuccessAt = OffsetDateTime.now()
        stock.avRetryCount = 0
        stock.avErrorCode = null
        stock.avErrorMessage = null
        stockRepository.save(stock)
        log.debug("Successfully enriched stock {} from stored payload", stock.ticker)
        return true
    }

    private fun parseRawPayload(rawJson: String): AVOverviewResponse? {
        return try {
            objectMapper.readValue(rawJson, AVOverviewResponse::class.java)
        } catch (e: Exception) {
            log.error("Failed to parse raw payload: {}", e.message)
            null
        }
    }

    private fun mapResponseToStock(stock: Stock, response: AVOverviewResponse) {
        // Basic info
        stock.avAssetType = response.assetType
        stock.avDescription = response.description
        stock.avCik = response.cik
        stock.avSector = response.sector
        stock.avIndustry = response.industry
        stock.avAddress = response.address
        stock.avOfficialSite = response.officialSite
        stock.avFiscalYearEnd = response.fiscalYearEnd
        stock.avLatestQuarter = parseDate(response.latestQuarter)

        // Financial metrics
        stock.avMarketCap = response.parseDecimal(response.marketCapitalization)
        stock.avEbitda = response.parseDecimal(response.ebitda)
        stock.avPeRatio = response.parseDecimal(response.peRatio)
        stock.avPegRatio = response.parseDecimal(response.pegRatio)
        stock.avBookValue = response.parseDecimal(response.bookValue)
        stock.avDividendPerShare = response.parseDecimal(response.dividendPerShare)
        stock.avDividendYield = response.parseDecimal(response.dividendYield)
        stock.avEps = response.parseDecimal(response.eps)
        stock.avRevenuePerShareTtm = response.parseDecimal(response.revenuePerShareTTM)
        stock.avProfitMargin = response.parseDecimal(response.profitMargin)
        stock.avOperatingMarginTtm = response.parseDecimal(response.operatingMarginTTM)
        stock.avReturnOnAssetsTtm = response.parseDecimal(response.returnOnAssetsTTM)
        stock.avReturnOnEquityTtm = response.parseDecimal(response.returnOnEquityTTM)
        stock.avRevenueTtm = response.parseDecimal(response.revenueTTM)
        stock.avGrossProfitTtm = response.parseDecimal(response.grossProfitTTM)
        stock.avQuarterlyEarningsGrowthYoy = response.parseDecimal(response.quarterlyEarningsGrowthYOY)
        stock.avQuarterlyRevenueGrowthYoy = response.parseDecimal(response.quarterlyRevenueGrowthYOY)

        // Analyst ratings
        stock.avAnalystTargetPrice = response.parseDecimal(response.analystTargetPrice)
        stock.avAnalystRatingStrongBuy = response.parseInt(response.analystRatingStrongBuy)
        stock.avAnalystRatingBuy = response.parseInt(response.analystRatingBuy)
        stock.avAnalystRatingHold = response.parseInt(response.analystRatingHold)
        stock.avAnalystRatingSell = response.parseInt(response.analystRatingSell)
        stock.avAnalystRatingStrongSell = response.parseInt(response.analystRatingStrongSell)

        // Price metrics
        stock.avTrailingPe = response.parseDecimal(response.trailingPE)
        stock.avForwardPe = response.parseDecimal(response.forwardPE)
        stock.av52WeekHigh = response.parseDecimal(response.week52High)
        stock.av52WeekLow = response.parseDecimal(response.week52Low)
        stock.av50DayMa = response.parseDecimal(response.day50MovingAverage)
        stock.av200DayMa = response.parseDecimal(response.day200MovingAverage)
        stock.avSharesOutstanding = response.parseLong(response.sharesOutstanding)
        stock.avBeta = response.parseDecimal(response.beta)

        // Valuation ratios
        stock.avPriceToSalesRatioTtm = response.parseDecimal(response.priceToSalesRatioTTM)
        stock.avPriceToBookRatio = response.parseDecimal(response.priceToBookRatio)
        stock.avEvToRevenue = response.parseDecimal(response.evToRevenue)
        stock.avEvToEbitda = response.parseDecimal(response.evToEbitda)
        stock.avDilutedEpsTtm = response.parseDecimal(response.dilutedEpsTTM)

        // Shares & Ownership
        stock.avSharesFloat = response.parseLong(response.sharesFloat)
        stock.avPercentInsiders = response.parseDecimal(response.percentInsiders)
        stock.avPercentInstitutions = response.parseDecimal(response.percentInstitutions)

        // Dividend dates
        stock.avDividendDate = parseDate(response.dividendDate)
        stock.avExDividendDate = parseDate(response.exDividendDate)

        stock.updatedAt = OffsetDateTime.now()
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank() || dateStr == "None" || dateStr == "-") {
            return null
        }
        return try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            null
        }
    }

    private fun handleEnrichmentError(stock: Stock, e: Exception, step: IngestionStep) {
        stock.avEnrichmentStatus = AVEnrichmentStatus.FAILED_RETRYABLE
        stock.avRetryCount = (stock.avRetryCount) + 1
        stock.avErrorCode = "UNKNOWN"
        stock.avErrorMessage = e.message?.take(500)
        stockRepository.save(stock)

        trackingService.logError(
            stepId = step.id,
            errorType = ErrorType.PARSE_ERROR,
            message = e.message ?: "Unknown error",
            context = mapOf<String, Any>("ticker" to stock.ticker)
        )
    }
}
