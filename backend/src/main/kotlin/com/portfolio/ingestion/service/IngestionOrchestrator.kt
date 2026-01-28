package com.portfolio.ingestion.service

import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.*
import com.portfolio.ingestion.service.alphavantage.AVEtfEnrichmentService
import com.portfolio.ingestion.service.alphavantage.AVEtfIngestionService
import com.portfolio.ingestion.service.alphavantage.AVStockEnrichmentService
import com.portfolio.ingestion.service.alphavantage.AVStockIngestionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IngestionOrchestrator(
    private val config: IngestionConfig,
    private val eodhdService: EodhdIngestionService,
    private val avStockIngestionService: AVStockIngestionService,
    private val avEtfIngestionService: AVEtfIngestionService,
    private val avStockEnrichmentService: AVStockEnrichmentService,
    private val avEtfEnrichmentService: AVEtfEnrichmentService,
    private val trackingService: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs the full ingestion pipeline including:
     * 1. EODHD Universe refresh
     * 2. Alpha Vantage Stock ingestion (fetch raw data)
     * 3. Alpha Vantage ETF ingestion (fetch raw data)
     * 4. Alpha Vantage Stock enrichment (parse raw payload)
     * 5. Alpha Vantage ETF enrichment (parse raw payload)
     *
     * Note: Mutual fund enrichment is skipped (not supported by Alpha Vantage)
     */
    fun runFullIngestion(triggerSource: String): IngestionRun {
        val runType = if (triggerSource == "scheduler") RunType.SCHEDULED else RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting ingestion run ${run.id} (type=$runType, source=$triggerSource)")

        var overallSuccess = true

        try {
            // Step 1: EODHD Universe refresh
            val eodhdSuccess = runEodhdUniverse(run)
            if (!eodhdSuccess) {
                overallSuccess = false
            }

            // Steps 2-5: Alpha Vantage ingestion and enrichment (if enabled)
            if (config.alphavantage.enabled) {
                // Step 2: Stock ingestion (fetch raw data)
                val avStockIngestionSuccess = runAvStockIngestion(run)
                if (!avStockIngestionSuccess) overallSuccess = false

                // Step 3: ETF ingestion (fetch raw data)
                val avEtfIngestionSuccess = runAvEtfIngestion(run)
                if (!avEtfIngestionSuccess) overallSuccess = false

                // Step 4: Stock enrichment (parse raw payload)
                val avStockEnrichmentSuccess = runAvStockEnrichment(run)
                if (!avStockEnrichmentSuccess) overallSuccess = false

                // Step 5: ETF enrichment (parse raw payload)
                val avEtfEnrichmentSuccess = runAvEtfEnrichment(run)
                if (!avEtfEnrichmentSuccess) overallSuccess = false
            } else {
                log.info("Alpha Vantage enrichment is disabled, skipping AV steps")
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
    // Alpha Vantage ingestion methods (fetch raw data)
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
    private fun runAvEtfIngestion(run: IngestionRun): Boolean {
        log.info("Starting Alpha Vantage ETF Ingestion")

        val step = trackingService.startStep(run, StepName.AV_ETF_INGESTION)

        return try {
            val result = avEtfIngestionService.ingestEtfs(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("AV ETF Ingestion completed: processed=${result.processed}, updated=${result.updated}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("AV ETF Ingestion failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    // ========================================
    // Alpha Vantage enrichment methods (parse raw payload)
    // ========================================

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

    @Transactional
    private fun runAvEtfEnrichment(run: IngestionRun): Boolean {
        log.info("Starting Alpha Vantage ETF Enrichment")

        val step = trackingService.startStep(run, StepName.AV_ETF_ENRICHMENT)

        return try {
            val result = avEtfEnrichmentService.enrichEtfs(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("AV ETF Enrichment completed: processed=${result.processed}, updated=${result.updated}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("AV ETF Enrichment failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    // ========================================
    // Convenience methods for granular control
    // ========================================

    /**
     * Run the universe refresh only (without ingestion or enrichment)
     */
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

    /**
     * Run Alpha Vantage Stock ingestion only (fetch raw data).
     */
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

    /**
     * Run Alpha Vantage ETF ingestion only (fetch raw data).
     */
    fun runEtfIngestionOnly(triggerSource: String): IngestionRun {
        val runType = RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting AV ETF-only ingestion run ${run.id}")

        try {
            val success = runAvEtfIngestion(run)
            val finalStatus = if (success) RunStatus.COMPLETED else RunStatus.FAILED

            trackingService.completeRun(run, finalStatus)
            log.info("Completed AV ETF-only ingestion run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during AV ETF ingestion run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }

    /**
     * Run Alpha Vantage Stock enrichment only (parse stored raw payload).
     */
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

    /**
     * Run Alpha Vantage ETF enrichment only (parse stored raw payload).
     */
    fun runEtfEnrichmentOnly(triggerSource: String): IngestionRun {
        val runType = RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting AV ETF-only enrichment run ${run.id}")

        try {
            val success = runAvEtfEnrichment(run)
            val finalStatus = if (success) RunStatus.COMPLETED else RunStatus.FAILED

            trackingService.completeRun(run, finalStatus)
            log.info("Completed AV ETF-only enrichment run ${run.id} with status $finalStatus")

        } catch (e: Exception) {
            log.error("Critical error during AV ETF enrichment run ${run.id}: ${e.message}", e)
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }

        return run
    }
}
