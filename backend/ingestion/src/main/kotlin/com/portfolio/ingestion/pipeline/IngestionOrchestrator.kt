package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.persistence.entity.*
import com.portfolio.ingestion.tracking.IngestionTrackingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IngestionOrchestrator(
    private val exchangeSyncStep: ExchangeSyncStep,
    private val universeSyncStep: UniverseSyncStep,
    private val rawDataFetchStep: RawDataFetchStep,
    private val tracking: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var activeRunId: Long? = null

    fun getActiveRunId(): Long? = activeRunId
    fun isRunning(): Boolean = activeRunId != null

    suspend fun runExchangeSync(triggerSource: String) {
        val run = tracking.startRun(RunType.MANUAL, triggerSource)
        activeRunId = run.id
        val step = tracking.startStep(run, StepName.EXCHANGE_SYNC)
        try {
            val result = exchangeSyncStep.execute(step)
            tracking.completeStep(step, StepStatus.COMPLETED, result.processed, result.created, result.updated, result.failed)
            tracking.completeRun(run, RunStatus.COMPLETED)
            activeRunId = null
        } catch (e: Exception) {
            log.error("Exchange sync failed", e)
            tracking.completeStep(step, StepStatus.FAILED, 0, 0, 0, 0)
            tracking.completeRun(run, RunStatus.FAILED)
            activeRunId = null
        }
    }

    suspend fun runFullIngestion(triggerSource: String) {
        val run = tracking.startRun(RunType.SCHEDULED, triggerSource)
        activeRunId = run.id
        var hasFailures = false

        // Step 1: Universe sync
        val universeStep = tracking.startStep(run, StepName.UNIVERSE_SYNC)
        try {
            val result = universeSyncStep.execute(universeStep)
            tracking.completeStep(universeStep, StepStatus.COMPLETED, result.processed, result.created, result.updated, result.failed)
            if (result.failed > 0) hasFailures = true
        } catch (e: Exception) {
            log.error("Universe sync failed", e)
            tracking.completeStep(universeStep, StepStatus.FAILED, 0, 0, 0, 0)
            hasFailures = true
        }

        // Step 2: Raw data fetch
        val fetchStep = tracking.startStep(run, StepName.RAW_DATA_FETCH)
        try {
            val result = rawDataFetchStep.execute(fetchStep)
            tracking.completeStep(fetchStep, StepStatus.COMPLETED, result.processed, 0, result.updated, result.failed)
            if (result.failed > 0) hasFailures = true
        } catch (e: Exception) {
            log.error("Raw data fetch failed", e)
            tracking.completeStep(fetchStep, StepStatus.FAILED, 0, 0, 0, 0)
            hasFailures = true
        }

        tracking.completeRun(run, if (hasFailures) RunStatus.PARTIAL else RunStatus.COMPLETED)
        activeRunId = null
        log.info("Full ingestion complete: status={}", run.status)
    }
}
