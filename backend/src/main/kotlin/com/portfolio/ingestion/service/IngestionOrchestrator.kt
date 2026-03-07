package com.portfolio.ingestion.service

import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.*
import com.portfolio.ingestion.service.alphavantage.AVStockEnrichmentService
import com.portfolio.ingestion.service.alphavantage.AVStockIngestionService
import com.portfolio.ingestion.service.etfcom.EtfComEnrichmentService
import com.portfolio.ingestion.service.etfcom.EtfComUniverseService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IngestionOrchestrator(
    private val config: IngestionConfig,
    private val eodhdService: EodhdIngestionService,
    private val avStockIngestionService: AVStockIngestionService,
    private val avStockEnrichmentService: AVStockEnrichmentService,
    private val etfComUniverseService: EtfComUniverseService,
    private val etfComEnrichmentService: EtfComEnrichmentService,
    private val trackingService: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs the full ingestion pipeline:
     * 1. EODHD Universe refresh (stocks & mutual funds; ETFs skipped when etf.com enabled)
     * 2. etf.com ETF Universe refresh (when enabled)
     * 3. Alpha Vantage Stock ingestion (fetch raw data)
     * 4. Alpha Vantage Stock enrichment (parse raw payload)
     * 5. etf.com ETF enrichment (when enabled)
     */
    fun runFullIngestion(triggerSource: String): IngestionRun {
        val runType = if (triggerSource == "scheduler") RunType.SCHEDULED else RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting ingestion run ${run.id} (type=$runType, source=$triggerSource)")

        var overallSuccess = true

        try {
            // Step 1: EODHD Universe refresh (stocks & mutual funds)
            val eodhdSuccess = runEodhdUniverse(run)
            if (!eodhdSuccess) overallSuccess = false

            // Step 2: etf.com ETF Universe refresh
            if (config.etfcom.enabled) {
                val etfComUniverseSuccess = runEtfComUniverse(run)
                if (!etfComUniverseSuccess) overallSuccess = false
            }

            // Steps 3-4: Alpha Vantage stock ingestion and enrichment
            if (config.alphavantage.enabled) {
                val avStockIngestionSuccess = runAvStockIngestion(run)
                if (!avStockIngestionSuccess) overallSuccess = false

                val avStockEnrichmentSuccess = runAvStockEnrichment(run)
                if (!avStockEnrichmentSuccess) overallSuccess = false
            } else {
                log.info("Alpha Vantage enrichment is disabled, skipping AV steps")
            }

            // Step 5: etf.com ETF enrichment
            if (config.etfcom.enabled) {
                val etfComEnrichmentSuccess = runEtfComEnrichment(run)
                if (!etfComEnrichmentSuccess) overallSuccess = false
            }

            val finalStatus = if (overallSuccess) RunStatus.COMPLETED else RunStatus.PARTIAL

            trackingService.completeRun(run, finalStatus)
            log.info("Completed ingestion run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during ingestion run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }

    @Transactional
    private fun runEodhdUniverse(run: IngestionRun): Boolean {
        log.info("Starting EODHD Universe Refresh")

        val step = trackingService.startStep(run, StepName.EODHD_UNIVERSE)

        return try {
            val result = eodhdService.refreshUniverse(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("EODHD Universe completed: processed=${result.processed}, created=${result.created}, updated=${result.updated}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("EODHD Universe failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    // ========================================
    // etf.com methods
    // ========================================

    @Transactional
    private fun runEtfComUniverse(run: IngestionRun): Boolean {
        log.info("Starting etf.com ETF Universe Refresh")

        val step = trackingService.startStep(run, StepName.ETFCOM_ETF_UNIVERSE)

        return try {
            val result = etfComUniverseService.refreshUniverse(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("etf.com Universe completed: processed=${result.processed}, created=${result.created}, updated=${result.updated}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("etf.com Universe failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    @Transactional
    private fun runEtfComEnrichment(run: IngestionRun): Boolean {
        log.info("Starting etf.com ETF Enrichment")

        val step = trackingService.startStep(run, StepName.ETFCOM_ETF_ENRICHMENT)

        return try {
            val result = etfComEnrichmentService.enrichEtfs(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("etf.com ETF Enrichment completed: processed=${result.processed}, updated=${result.updated}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("etf.com ETF Enrichment failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    // ========================================
    // Alpha Vantage stock methods
    // ========================================

    @Transactional
    private fun runAvStockIngestion(run: IngestionRun): Boolean {
        log.info("Starting Alpha Vantage Stock Ingestion")

        val step = trackingService.startStep(run, StepName.AV_STOCK_INGESTION)

        return try {
            val result = avStockIngestionService.ingestStocks(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("AV Stock Ingestion completed: processed=${result.processed}, updated=${result.updated}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("AV Stock Ingestion failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    @Transactional
    private fun runAvStockEnrichment(run: IngestionRun): Boolean {
        log.info("Starting Alpha Vantage Stock Enrichment")

        val step = trackingService.startStep(run, StepName.AV_STOCK_ENRICHMENT)

        return try {
            val result = avStockEnrichmentService.enrichStocks(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("AV Stock Enrichment completed: processed=${result.processed}, updated=${result.updated}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("AV Stock Enrichment failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    // ========================================
    // Convenience methods for granular control
    // ========================================

    fun runUniverseRefreshOnly(triggerSource: String): IngestionRun {
        val runType = if (triggerSource == "scheduler") RunType.SCHEDULED else RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting universe-only ingestion run ${run.id} (type=$runType, source=$triggerSource)")

        try {
            val stepResult = runEodhdUniverse(run)

            val finalStatus = if (stepResult) RunStatus.COMPLETED else RunStatus.FAILED

            trackingService.completeRun(run, finalStatus)
            log.info("Completed universe-only ingestion run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during ingestion run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }

    fun runStockIngestionOnly(triggerSource: String): IngestionRun {
        val runType = RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting AV Stock-only ingestion run ${run.id}")

        try {
            val success = runAvStockIngestion(run)
            val finalStatus = if (success) RunStatus.COMPLETED else RunStatus.FAILED

            trackingService.completeRun(run, finalStatus)
            log.info("Completed AV Stock-only ingestion run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during AV Stock ingestion run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }

    fun runStockEnrichmentOnly(triggerSource: String): IngestionRun {
        val runType = RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting AV Stock-only enrichment run ${run.id}")

        try {
            val success = runAvStockEnrichment(run)
            val finalStatus = if (success) RunStatus.COMPLETED else RunStatus.FAILED

            trackingService.completeRun(run, finalStatus)
            log.info("Completed AV Stock-only enrichment run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during AV Stock enrichment run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }

    fun runEtfComUniverseOnly(triggerSource: String): IngestionRun {
        val runType = RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting etf.com universe-only run ${run.id}")

        try {
            val success = runEtfComUniverse(run)
            val finalStatus = if (success) RunStatus.COMPLETED else RunStatus.FAILED

            trackingService.completeRun(run, finalStatus)
            log.info("Completed etf.com universe-only run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during etf.com universe run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }

    fun runEtfComEnrichmentOnly(triggerSource: String): IngestionRun {
        val runType = RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting etf.com enrichment-only run ${run.id}")

        try {
            val success = runEtfComEnrichment(run)
            val finalStatus = if (success) RunStatus.COMPLETED else RunStatus.FAILED

            trackingService.completeRun(run, finalStatus)
            log.info("Completed etf.com enrichment-only run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during etf.com enrichment run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }
}
