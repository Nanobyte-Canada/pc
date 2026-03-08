package com.portfolio.ingestion.service.etfcom

import com.portfolio.entity.Etf
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.BatchResult
import com.portfolio.repository.EtfRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EtfComEnrichmentBatchProcessor(
    private val etfRepository: EtfRepository,
    private val singleEtfProcessor: EtfComSingleEtfEnrichmentProcessor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processBatch(batch: List<Etf>, step: IngestionStep): BatchResult {
        var updated = 0
        var failed = 0

        for (etf in batch) {
            try {
                val enriched = singleEtfProcessor.enrichEtf(etf, step)
                if (enriched) {
                    updated++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                log.error("Unexpected error enriching ETF {} from etf.com: {}", etf.symbol, e.message, e)

                try {
                    val freshEtf = etfRepository.findById(etf.id).orElse(null)
                    if (freshEtf != null) {
                        singleEtfProcessor.handleEnrichmentError(freshEtf, e, step)
                    }
                } catch (errorHandlingEx: Exception) {
                    log.error("Failed to record error for ETF {}: {}", etf.symbol, errorHandlingEx.message)
                }

                failed++
            }
        }

        return BatchResult(updated = updated, failed = failed, processed = batch.size)
    }
}
