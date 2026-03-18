package com.portfolio.ingestion.service

import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.entity.*
import com.portfolio.ingestion.service.alphavantage.AVStockIngestionService
import com.portfolio.ingestion.service.etfcom.EtfComEnrichmentService
import com.portfolio.ingestion.service.etfcom.EtfComUniverseService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture

@Service
class IngestionOrchestrator(
    private val config: IngestionConfig,
    private val eodhdService: EodhdIngestionService,
    private val avStockIngestionService: AVStockIngestionService,
    private val etfComUniverseService: EtfComUniverseService,
    private val etfComEnrichmentService: EtfComEnrichmentService,
    private val trackingService: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs the full ingestion pipeline with stock and ETF pipelines in parallel:
     *   Stock pipeline: EODHD Universe → Alpha Vantage Stock Enrichment
     *   ETF pipeline:   etf.com Universe → etf.com ETF Enrichment
     */
    fun runFullIngestion(triggerSource: String): IngestionRun {
        val runType = if (triggerSource == "scheduler") RunType.SCHEDULED else RunType.MANUAL
        val run = trackingService.startRun(runType, triggerSource)

        log.info("Starting ingestion run ${run.id} (type=$runType, source=$triggerSource) — stock + ETF pipelines in parallel")

        var overallSuccess = true

        try {
            val stockFuture = CompletableFuture.runAsync {
                val eodhdSuccess = runEodhdUniverse(run)
                if (!eodhdSuccess) overallSuccess = false

                if (config.alphavantage.enabled) {
                    val avSuccess = runAvStockIngestion(run)
                    if (!avSuccess) overallSuccess = false
                }
            }

            val etfFuture = CompletableFuture.runAsync {
                if (config.etfcom.enabled) {
                    val universeSuccess = runEtfComUniverse(run)
                    if (!universeSuccess) overallSuccess = false

                    val enrichmentSuccess = runEtfComEnrichment(run)
                    if (!enrichmentSuccess) overallSuccess = false
                }
            }

            CompletableFuture.allOf(stockFuture, etfFuture).join()

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
            log.info("EODHD Universe completed: processed=${result.processed}, created=${result.created}, failed=${result.failed}")
            true
        } catch (e: Exception) {
            log.error("EODHD Universe failed: ${e.message}", e)
            trackingService.failStep(step, e)
            false
        }
    }

    @Transactional
    private fun runEtfComUniverse(run: IngestionRun): Boolean {
        log.info("Starting etf.com ETF Universe Refresh")
        val step = trackingService.startStep(run, StepName.ETFCOM_ETF_UNIVERSE)
        return try {
            val result = etfComUniverseService.refreshUniverse(step)
            trackingService.completeStep(step, result, StepStatus.COMPLETED)
            log.info("etf.com Universe completed: processed=${result.processed}, created=${result.created}, failed=${result.failed}")
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

    @Transactional
    private fun runAvStockIngestion(run: IngestionRun): Boolean {
        log.info("Starting Alpha Vantage Stock Enrichment")
        val step = trackingService.startStep(run, StepName.AV_STOCK_INGESTION)
        return try {
            val result = avStockIngestionService.ingestStocks(step)
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
    // Granular single-step runs
    // ========================================

    fun runUniverseRefreshOnly(triggerSource: String): IngestionRun {
        val run = trackingService.startRun(RunType.MANUAL, triggerSource)
        try {
            val success = runEodhdUniverse(run)
            trackingService.completeRun(run, if (success) RunStatus.COMPLETED else RunStatus.FAILED)
        } catch (e: Exception) {
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }
        return run
    }

    fun runStockIngestionOnly(triggerSource: String): IngestionRun {
        val run = trackingService.startRun(RunType.MANUAL, triggerSource)
        try {
            val success = runAvStockIngestion(run)
            trackingService.completeRun(run, if (success) RunStatus.COMPLETED else RunStatus.FAILED)
        } catch (e: Exception) {
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }
        return run
    }

    fun runEtfComUniverseOnly(triggerSource: String): IngestionRun {
        val run = trackingService.startRun(RunType.MANUAL, triggerSource)
        try {
            val success = runEtfComUniverse(run)
            trackingService.completeRun(run, if (success) RunStatus.COMPLETED else RunStatus.FAILED)
        } catch (e: Exception) {
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }
        return run
    }

    fun runEtfComEnrichmentOnly(triggerSource: String): IngestionRun {
        val run = trackingService.startRun(RunType.MANUAL, triggerSource)
        try {
            val success = runEtfComEnrichment(run)
            trackingService.completeRun(run, if (success) RunStatus.COMPLETED else RunStatus.FAILED)
        } catch (e: Exception) {
            trackingService.completeRun(run, RunStatus.FAILED)
            throw e
        }
        return run
    }
}
