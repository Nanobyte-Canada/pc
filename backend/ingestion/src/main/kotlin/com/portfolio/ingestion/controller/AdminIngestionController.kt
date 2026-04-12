package com.portfolio.ingestion.controller

import com.portfolio.ingestion.persistence.entity.InstrumentType
import com.portfolio.ingestion.persistence.repository.*
import com.portfolio.ingestion.pipeline.IngestionOrchestrator
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import com.portfolio.ingestion.config.IngestionProperties
import kotlinx.coroutines.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/ingestion")
class AdminIngestionController(
    private val orchestrator: IngestionOrchestrator,
    private val runRepo: IngestionRunRepository,
    private val stepRepo: IngestionStepRepository,
    private val errorRepo: IngestionErrorRepository,
    private val instrumentRepo: InstrumentRepository,
    private val rawDataRepo: ProviderRawDataRepository,
    private val exchangeRepo: ExchangeRepository,
    private val rateLimiter: EodhdRateLimiter,
    private val props: IngestionProperties
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @PostMapping("/exchanges")
    fun syncExchanges(): ResponseEntity<Map<String, Any>> {
        if (orchestrator.isRunning()) {
            return ResponseEntity.status(409).body(mapOf("status" to "error", "message" to "An ingestion run is already in progress"))
        }
        scope.launch {
            orchestrator.runExchangeSync("api:/admin/ingestion/exchanges")
        }
        return ResponseEntity.ok(mapOf("status" to "started"))
    }

    @PostMapping("/run")
    fun triggerFullIngestion(): ResponseEntity<Map<String, Any>> {
        if (orchestrator.isRunning()) {
            return ResponseEntity.status(409).body(mapOf("status" to "error", "message" to "An ingestion run is already in progress"))
        }
        rateLimiter.resetDailyQuota()
        scope.launch {
            orchestrator.runFullIngestion("api:/admin/ingestion/run")
        }
        return ResponseEntity.ok(mapOf("status" to "started"))
    }

    @GetMapping("/active-run")
    fun getActiveRun(): ResponseEntity<Map<String, Any?>> {
        val runId = orchestrator.getActiveRunId()
        if (runId == null) {
            return ResponseEntity.ok(mapOf("isRunning" to false))
        }
        val steps = stepRepo.findByRunId(runId).map { step ->
            mapOf(
                "name" to step.stepName.name,
                "status" to step.status.name,
                "processed" to step.recordsProcessed,
                "created" to step.recordsCreated,
                "updated" to step.recordsUpdated,
                "failed" to step.recordsFailed
            )
        }
        return ResponseEntity.ok(mapOf(
            "isRunning" to true,
            "runId" to runId,
            "steps" to steps
        ))
    }

    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Map<String, Any?>> {
        val totalInstruments = instrumentRepo.count()
        val enrichedCount = rawDataRepo.count()
        val remaining = rateLimiter.remainingDailyQuota()
        val exchangeCount = exchangeRepo.countByIsActiveTrue()

        val byType = instrumentRepo.countByType().associate {
            (it[0] as InstrumentType).name to it[1] as Long
        }
        val enrichedByType = rawDataRepo.countEnrichedByType().associate {
            (it[0] as InstrumentType).name to it[1] as Long
        }
        val instrumentsByType = InstrumentType.entries.associate { type ->
            type.name to mapOf(
                "total" to (byType[type.name] ?: 0L),
                "enriched" to (enrichedByType[type.name] ?: 0L)
            )
        }

        val lastRun = runRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, 1)).firstOrNull()

        return ResponseEntity.ok(mapOf(
            "totalInstruments" to totalInstruments,
            "enrichedInstruments" to enrichedCount,
            "pendingInstruments" to (totalInstruments - enrichedCount),
            "remainingDailyQuota" to remaining,
            "totalDailyQuota" to props.eodhd.dailyQuota,
            "exchangeCount" to exchangeCount,
            "exchanges" to props.targetExchanges,
            "lastRunStatus" to (lastRun?.status?.name ?: "NONE"),
            "lastRunCompletedAt" to lastRun?.completedAt,
            "instrumentsByType" to instrumentsByType
        ))
    }

    @GetMapping("/runs")
    fun listRuns(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<Any> {
        val runs = runRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
        return ResponseEntity.ok(runs.map { run ->
            mapOf(
                "id" to run.id,
                "runType" to run.runType,
                "status" to run.status,
                "startedAt" to run.startedAt,
                "completedAt" to run.completedAt,
                "triggerSource" to run.triggerSource
            )
        })
    }

    @GetMapping("/runs/{id}/steps")
    fun getRunSteps(@PathVariable id: Long): ResponseEntity<Any> {
        val steps = stepRepo.findByRunId(id)
        return ResponseEntity.ok(steps.map { step ->
            mapOf(
                "id" to step.id,
                "stepName" to step.stepName,
                "status" to step.status,
                "recordsProcessed" to step.recordsProcessed,
                "recordsCreated" to step.recordsCreated,
                "recordsUpdated" to step.recordsUpdated,
                "recordsFailed" to step.recordsFailed,
                "startedAt" to step.startedAt,
                "completedAt" to step.completedAt
            )
        })
    }

    @GetMapping("/runs/{id}/errors")
    fun getRunErrors(@PathVariable id: Long): ResponseEntity<Any> {
        val errors = errorRepo.findByStepRunIdOrderByCreatedAtDesc(id, PageRequest.of(0, 100))
        return ResponseEntity.ok(errors.map { error ->
            mapOf(
                "id" to error.id,
                "errorType" to error.errorType,
                "errorCode" to error.errorCode,
                "errorMessage" to error.errorMessage,
                "createdAt" to error.createdAt
            )
        })
    }
}
