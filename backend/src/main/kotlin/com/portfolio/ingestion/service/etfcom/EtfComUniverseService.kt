package com.portfolio.ingestion.service.etfcom

import com.portfolio.entity.EtfComEnrichmentStatus
import com.portfolio.entity.Etf
import com.portfolio.ingestion.client.etfcom.EtfComClient
import com.portfolio.ingestion.dto.etfcom.EtfComTickerDto
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.ingestion.service.StepResult
import com.portfolio.repository.EtfRepository
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
    private val trackingService: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun refreshUniverse(step: IngestionStep): StepResult {
        log.info("Starting etf.com universe refresh")
        val now = OffsetDateTime.now()

        val tickers = etfComClient.fetchAllTickers()
        log.info("Fetched {} tickers from etf.com", tickers.size)

        var created = 0
        var updated = 0
        var failed = 0
        val seenSymbols = mutableSetOf<String>()

        for (tickerDto in tickers) {
            try {
                val result = processEtfTicker(tickerDto, now)
                seenSymbols.add(tickerDto.ticker.uppercase())
                if (result.created) created++
                else if (result.updated) updated++
            } catch (e: Exception) {
                log.error("Error processing etf.com ticker {}: {}", tickerDto.ticker, e.message)
                trackingService.logParseError(step.id, tickerDto.ticker, e)
                failed++
            }
        }

        // Mark ETFs not seen in this run as inactive
        val staleCount = markStaleEtfs(now)
        log.info("Marked {} ETFs as stale", staleCount)

        log.info("etf.com universe refresh complete: created={}, updated={}, failed={}, stale={}", created, updated, failed, staleCount)

        return StepResult(
            processed = tickers.size,
            created = created,
            updated = updated,
            failed = failed,
            metadata = mapOf(
                "totalTickers" to tickers.size,
                "staleCount" to staleCount
            )
        )
    }

    private data class ProcessResult(val created: Boolean, val updated: Boolean)

    private fun processEtfTicker(dto: EtfComTickerDto, seenAt: OffsetDateTime): ProcessResult {
        val symbol = dto.ticker.uppercase().trim()

        val existing = etfRepository.findBySymbolIgnoreCase(symbol)

        if (existing != null) {
            existing.apply {
                name = dto.fund ?: name
                issuer = dto.issuer ?: issuer
                inceptionDate = parseDate(dto.inceptionDate) ?: inceptionDate
                assetClass = dto.assetClass ?: assetClass
                etfcomFundId = dto.fundId
                etfcomAssetClass = dto.assetClass
                sourceLastSeenAt = seenAt
                isActive = true
                updatedAt = OffsetDateTime.now()
            }
            etfRepository.save(existing)
            return ProcessResult(created = false, updated = true)
        }

        etfRepository.save(Etf(
            symbol = symbol,
            exchange = "US",
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
        return ProcessResult(created = true, updated = false)
    }

    private fun markStaleEtfs(currentRunTime: OffsetDateTime): Int {
        val cutoff = currentRunTime.minusMinutes(1)
        val staleEtfs = etfRepository.findStaleEtfs(cutoff)
        var count = 0
        for (etf in staleEtfs) {
            // Only mark as stale if etfcom_fund_id is set (i.e., came from etf.com)
            if (etf.etfcomFundId != null) {
                etf.isActive = false
                etf.updatedAt = OffsetDateTime.now()
                count++
            }
        }
        if (count > 0) {
            etfRepository.saveAll(staleEtfs.filter { it.etfcomFundId != null && !it.isActive })
        }
        return count
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
