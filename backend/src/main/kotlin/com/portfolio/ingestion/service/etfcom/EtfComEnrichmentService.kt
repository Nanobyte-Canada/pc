package com.portfolio.ingestion.service.etfcom

import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.StepResult
import com.portfolio.repository.EtfRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class EtfComEnrichmentService(
    private val config: IngestionConfig,
    private val etfRepository: EtfRepository,
    private val batchProcessor: EtfComEnrichmentBatchProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun enrichEtfs(step: IngestionStep): StepResult {
        val etfComConfig = config.etfcom
        val batchSize = etfComConfig.batchSize

        var totalProcessed = 0
        var totalUpdated = 0
        var totalFailed = 0
        var batchNumber = 0
        var page = 0

        log.info("Starting etf.com ETF enrichment (batchSize={})", batchSize)

        while (true) {
            val pageRequest = PageRequest.of(page, batchSize, Sort.by("id"))
            val batch = etfRepository.findAll(pageRequest)
            if (!batch.hasContent()) break
            page++

            batchNumber++
            log.info("Processing etf.com enrichment batch {} ({} ETFs)", batchNumber, batch.content.size)

            val result = batchProcessor.processBatch(batch.content, step)
            totalProcessed += result.processed
            totalUpdated += result.updated
            totalFailed += result.failed

            log.info("Batch {} complete: processed={}, updated={}, failed={}",
                batchNumber, result.processed, result.updated, result.failed)

            Thread.sleep(etfComConfig.interBatchDelayMs)
        }

        log.info("etf.com ETF enrichment complete: totalProcessed={}, totalUpdated={}, totalFailed={}",
            totalProcessed, totalUpdated, totalFailed)

        return StepResult(
            processed = totalProcessed,
            created = 0,
            updated = totalUpdated,
            failed = totalFailed,
            metadata = mapOf("batches" to batchNumber)
        )
    }
}
