package com.portfolio.ingestion.service.etfcom

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.Etf
import com.portfolio.entity.EtfComEnrichmentStatus
import com.portfolio.ingestion.client.etfcom.EtfComApiResult
import com.portfolio.ingestion.client.etfcom.EtfComClient
import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.service.IngestionHashCacheService
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.EtfRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class EtfComSingleEtfEnrichmentProcessor(
    private val etfComClient: EtfComClient,
    private val etfRepository: EtfRepository,
    private val trackingService: IngestionTrackingService,
    private val hashCacheService: IngestionHashCacheService,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val enrichmentSuccess = Counter.builder("etfcom_enrichment_total")
        .tag("status", "success")
        .register(meterRegistry)

    private val enrichmentFailed = Counter.builder("etfcom_enrichment_total")
        .tag("status", "failed")
        .register(meterRegistry)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun enrichEtf(etf: Etf, step: IngestionStep): Boolean {
        etf.etfcomLastAttemptAt = OffsetDateTime.now()

        val result = etfComClient.fetchAllData(etf.symbol, etf.etfcomFundId)

        return when (result) {
            is EtfComApiResult.NotFound -> {
                log.debug("ETF {} not found on etf.com", etf.symbol)
                etf.etfcomEnrichmentStatus = EtfComEnrichmentStatus.FAILED_PERMANENT
                etf.etfcomErrorCode = "NOT_FOUND"
                etf.etfcomErrorMessage = "Ticker not found on etf.com"
                etfRepository.save(etf)
                enrichmentFailed.increment()
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
                enrichmentFailed.increment()
                false
            }

            is EtfComApiResult.Success -> {
                val data = result.data

                // Combine raw payloads into one JSON object — values are JsonNode so no double-encoding
                val combinedJson = objectMapper.writeValueAsString(data.rawPayloads)
                val sanitized = sanitizeEtfComReferences(combinedJson)

                if (!hashCacheService.isChanged("etf", etf.symbol, sanitized)) {
                    log.debug("ETF {} payload unchanged — skipping re-enrichment", etf.symbol)
                    etf.etfcomLastSuccessAt = OffsetDateTime.now()
                    etfRepository.save(etf)
                    return true
                }

                etf.etfcomRawPayload = objectMapper.readTree(sanitized)
                etf.etfcomEnrichmentStatus = EtfComEnrichmentStatus.SUCCESS
                etf.etfcomLastSuccessAt = OffsetDateTime.now()
                etf.etfcomRetryCount = 0
                etf.etfcomErrorCode = null
                etf.etfcomErrorMessage = null
                etf.updatedAt = OffsetDateTime.now()
                etfRepository.save(etf)

                log.debug("Successfully enriched ETF {} from etf.com", etf.symbol)
                enrichmentSuccess.increment()
                true
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleEnrichmentError(etf: Etf, e: Exception, step: IngestionStep) {
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

    private fun sanitizeEtfComReferences(json: String): String =
        json.replace(Regex("""https?://[^"]*etf\.com[^"]*"""), "")
            .replace(Regex("""//[^"]*etf\.com[^"]*"""), "")
}
