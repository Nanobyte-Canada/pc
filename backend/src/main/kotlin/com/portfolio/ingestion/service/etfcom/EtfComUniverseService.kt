package com.portfolio.ingestion.service.etfcom

import com.portfolio.entity.EtfComEnrichmentStatus
import com.portfolio.entity.Etf
import com.portfolio.ingestion.client.etfcom.EtfComClient
import com.portfolio.ingestion.dto.etfcom.EtfComTickerDto
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.ingestion.service.StepResult
import com.portfolio.repository.EtfHoldingRepository
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.EtfSectorAllocationFactsetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class EtfComUniverseService(
    private val etfComClient: EtfComClient,
    private val etfRepository: EtfRepository,
    private val etfHoldingRepository: EtfHoldingRepository,
    private val sectorRepository: EtfSectorAllocationFactsetRepository,
    private val trackingService: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun refreshUniverse(step: IngestionStep): StepResult {
        log.info("Starting etf.com universe refresh")
        val now = OffsetDateTime.now()

        val tickers = etfComClient.fetchAllTickers()
        log.info("Fetched {} tickers from etf.com", tickers.size)

        // Delete all existing ETF data for clean re-insert
        etfHoldingRepository.deleteAllInBatch()
        sectorRepository.deleteAllInBatch()
        etfRepository.deleteAllInBatch()
        log.info("Cleared existing ETF data for clean re-insert")

        var created = 0
        var failed = 0

        for (tickerDto in tickers) {
            try {
                processEtfTicker(tickerDto, now)
                created++
            } catch (e: Exception) {
                log.error("Error processing etf.com ticker {}: {}", tickerDto.ticker, e.message)
                trackingService.logParseError(step.id, tickerDto.ticker, e)
                failed++
            }
        }

        log.info("etf.com universe refresh complete: created={}, failed={}", created, failed)

        return StepResult(
            processed = tickers.size,
            created = created,
            updated = 0,
            failed = failed,
            metadata = mapOf(
                "totalTickers" to tickers.size
            )
        )
    }

    private fun processEtfTicker(dto: EtfComTickerDto, seenAt: OffsetDateTime) {
        val symbol = dto.ticker.uppercase().trim()

        etfRepository.save(Etf(
            symbol = symbol,
            name = dto.fund ?: symbol,
            currency = "USD",
            domicile = "USA",
            issuer = dto.issuer,
            inceptionDate = parseDate(dto.inceptionDate),
            assetClass = dto.assetClass,
            etfcomFundId = dto.fundId,
            etfcomAssetClass = dto.assetClass,
            isActive = true,
            sourceLastSeenAt = seenAt,
            etfcomEnrichmentStatus = EtfComEnrichmentStatus.PENDING
        ))
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
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
