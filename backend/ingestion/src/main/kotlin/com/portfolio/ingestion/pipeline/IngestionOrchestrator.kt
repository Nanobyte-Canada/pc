package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.persistence.entity.*
import com.portfolio.ingestion.persistence.repository.IngestionRunRepository
import com.portfolio.ingestion.tracking.IngestionTrackingService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class IngestionOrchestrator(
    private val exchangeSyncStep: ExchangeSyncStep,
    private val universeSyncStep: UniverseSyncStep,
    private val rawDataFetchStep: RawDataFetchStep,
    private val tracking: IngestionTrackingService,
    private val runRepo: IngestionRunRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var activeRunId: Long? = null

    fun getActiveRunId(): Long? = activeRunId
    fun isRunning(): Boolean = activeRunId != null

    @PostConstruct
    fun reconcileOrphanedRuns() {
        val orphaned = runRepo.findByStatus(RunStatus.RUNNING)
        if (orphaned.isNotEmpty()) {
            log.warn("Found {} orphaned RUNNING runs on startup, marking as FAILED", orphaned.size)
            orphaned.forEach { run ->
                run.status = RunStatus.FAILED
                run.completedAt = OffsetDateTime.now()
                runRepo.save(run)
                log.info("Marked orphaned run #{} as FAILED", run.id)
            }
        }
    }

    fun cancelRun() {
        val runId = activeRunId ?: return
        activeRunId = null
        try {
            runRepo.findById(runId).ifPresent { run ->
                if (run.status == RunStatus.RUNNING) {
                    run.status = RunStatus.FAILED
                    run.completedAt = OffsetDateTime.now()
                    runRepo.save(run)
                    log.info("Cancelled run #{}", runId)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to mark cancelled run #{} in DB", runId, e)
        }
    }

    suspend fun runExchangeSync(triggerSource: String) {
        val run = tracking.startRun(RunType.MANUAL, triggerSource)
        activeRunId = run.id
        try {
            val step = tracking.startStep(run, StepName.EXCHANGE_SYNC)
            try {
                val result = exchangeSyncStep.execute(step)
                tracking.completeStep(step, StepStatus.COMPLETED, result.processed, result.created, result.updated, result.failed)
                tracking.completeRun(run, RunStatus.COMPLETED)
            } catch (e: Exception) {
                log.error("Exchange sync failed", e)
                tracking.completeStep(step, StepStatus.FAILED, 0, 0, 0, 0)
                tracking.completeRun(run, RunStatus.FAILED)
            }
        } catch (e: Exception) {
            log.error("Exchange sync run failed unexpectedly", e)
            try { tracking.completeRun(run, RunStatus.FAILED) } catch (_: Exception) {}
        } finally {
            activeRunId = null
        }
    }

    suspend fun runFullIngestion(triggerSource: String) {
        val run = tracking.startRun(RunType.SCHEDULED, triggerSource)
        activeRunId = run.id
        try {
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
            log.info("Full ingestion complete: status={}", run.status)
        } catch (e: Exception) {
            log.error("Full ingestion run failed unexpectedly", e)
            try { tracking.completeRun(run, RunStatus.FAILED) } catch (_: Exception) {}
        } finally {
            activeRunId = null
        }
    }
}
