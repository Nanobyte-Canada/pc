package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.persistence.entity.IngestionStep
import com.portfolio.ingestion.persistence.repository.InstrumentExchangeRepository
import com.portfolio.ingestion.persistence.repository.InstrumentRepository
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class RawDataFetchStep(
    private val instrumentRepo: InstrumentRepository,
    private val instrumentExchangeRepo: InstrumentExchangeRepository,
    private val batchProcessor: FundamentalsBatchProcessor,
    private val rateLimiter: EodhdRateLimiter,
    private val props: IngestionProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(val processed: Int, val updated: Int, val skipped: Int, val failed: Int)

    suspend fun execute(step: IngestionStep): Result {
        val batchSize = props.eodhd.batchSize
        val maxInstruments = props.eodhd.dailyQuota / props.eodhd.fundamentalsCost
        var totalProcessed = 0
        var totalUpdated = 0
        var totalSkipped = 0
        var totalFailed = 0
        var page = 0

        while (true) {
            if (rateLimiter.remainingDailyQuota() < props.eodhd.fundamentalsCost * batchSize) {
                log.warn("Daily quota nearly exhausted, stopping. Remaining: {}", rateLimiter.remainingDailyQuota())
                break
            }

            val instruments = instrumentRepo.findStaleInstruments(PageRequest.of(page, batchSize))
            if (instruments.isEmpty()) break

            // Resolve exchange code for each instrument
            val items = instruments.map { instrument ->
                val ie = instrumentExchangeRepo.findByInstrumentIdAndExchangeId(instrument.id, 0)
                // Find any linked exchange for this instrument
                val exchangeCode = ie?.exchange?.code ?: props.targetExchanges.first()
                FundamentalsBatchProcessor.InstrumentWithExchange(instrument, exchangeCode)
            }

            val result = batchProcessor.processBatch(items, step)
            totalProcessed += result.processed
            totalUpdated += result.updated
            totalSkipped += result.skipped
            totalFailed += result.failed
            page++

            if (totalProcessed >= maxInstruments) {
                log.info("Reached daily instrument limit ({}), stopping", maxInstruments)
                break
            }
        }

        log.info("Raw data fetch: processed={}, updated={}, skipped={}, failed={}", totalProcessed, totalUpdated, totalSkipped, totalFailed)
        return Result(totalProcessed, totalUpdated, totalSkipped, totalFailed)
    }
}
