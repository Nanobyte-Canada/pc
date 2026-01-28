package com.portfolio.ingestion.controller

import com.portfolio.ingestion.entity.IngestionError
import com.portfolio.ingestion.entity.IngestionRun
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.repository.IngestionErrorRepository
import com.portfolio.ingestion.repository.IngestionRunRepository
import com.portfolio.ingestion.service.IngestionOrchestrator
import com.portfolio.ingestion.service.IngestionTrackingService
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

// DTOs for API responses
data class IngestionRunDto(
    val id: Long,
    val runType: String,
    val status: String,
    val triggerSource: String?,
    val startedAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
    val stepCount: Int
)

data class IngestionRunDetailDto(
    val id: Long,
    val runType: String,
    val status: String,
    val triggerSource: String?,
    val startedAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
    val steps: List<IngestionStepDto>
)

data class IngestionStepDto(
    val id: Long,
    val stepName: String,
    val status: String,
    val startedAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
    val recordsProcessed: Int,
    val recordsCreated: Int,
    val recordsUpdated: Int,
    val recordsFailed: Int,
    val errorCount: Int
)

data class IngestionErrorDto(
    val id: Long,
    val stepId: Long,
    val errorType: String,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime
)

data class TriggerIngestionRequest(
    val steps: List<String>? = null // null = run full ingestion (EODHD universe refresh)
)

data class TriggerIngestionResponse(
    val runId: Long,
    val status: String,
    val message: String
)

@RestController
@RequestMapping("/admin/ingestion")
class AdminIngestionController(
    private val orchestrator: IngestionOrchestrator,
    private val trackingService: IngestionTrackingService,
    private val runRepository: IngestionRunRepository,
    private val errorRepository: IngestionErrorRepository
) {

    /**
     * Trigger a full ingestion run.
     * Runs the complete pipeline:
     * 1. EODHD Universe refresh
     * 2. AV Stock ingestion (fetch raw data)
     * 3. AV ETF ingestion (fetch raw data)
     * 4. AV Stock enrichment (parse raw payload)
     * 5. AV ETF enrichment (parse raw payload)
     *
     * POST /admin/ingestion/run
     */
    @PostMapping("/run")
    fun triggerIngestion(
        @RequestBody(required = false) request: TriggerIngestionRequest?
    ): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runFullIngestion("api:/admin/ingestion/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Full ingestion run triggered successfully"
        ))
    }

    /**
     * Trigger universe refresh only (without AV ingestion/enrichment)
     * POST /admin/ingestion/run/universe
     */
    @PostMapping("/run/universe")
    fun triggerUniverseRefresh(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runUniverseRefreshOnly("api:/admin/ingestion/run/universe")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Universe refresh triggered successfully"
        ))
    }

    /**
     * Get list of recent ingestion runs
     * GET /admin/ingestion/runs?limit=10
     */
    @GetMapping("/runs")
    fun getRecentRuns(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<IngestionRunDto>> {
        val runs = trackingService.getRecentRuns(limit)
        return ResponseEntity.ok(runs.map { it.toDto() })
    }

    /**
     * Get details of a specific run
     * GET /admin/ingestion/runs/{id}
     */
    @GetMapping("/runs/{id}")
    fun getRunDetails(@PathVariable id: Long): ResponseEntity<IngestionRunDetailDto> {
        val run = trackingService.getRunWithDetails(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(run.toDetailDto())
    }

    /**
     * Get errors for a specific run
     * GET /admin/ingestion/runs/{id}/errors
     */
    @GetMapping("/runs/{id}/errors")
    fun getRunErrors(@PathVariable id: Long): ResponseEntity<List<IngestionErrorDto>> {
        val errors = errorRepository.findByRunId(id)
        return ResponseEntity.ok(errors.map { it.toDto() })
    }

    /**
     * Get recent errors across all runs
     * GET /admin/ingestion/errors?limit=50
     */
    @GetMapping("/errors")
    fun getRecentErrors(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<IngestionErrorDto>> {
        val errors = errorRepository.findRecentErrors(PageRequest.of(0, limit))
        return ResponseEntity.ok(errors.map { it.toDto() })
    }

    // Extension functions for DTO conversion
    private fun IngestionRun.toDto() = IngestionRunDto(
        id = id,
        runType = runType.name,
        status = status.name,
        triggerSource = triggerSource,
        startedAt = startedAt,
        completedAt = completedAt,
        stepCount = steps.size
    )

    private fun IngestionRun.toDetailDto() = IngestionRunDetailDto(
        id = id,
        runType = runType.name,
        status = status.name,
        triggerSource = triggerSource,
        startedAt = startedAt,
        completedAt = completedAt,
        steps = steps.map { it.toDto() }
    )

    private fun IngestionStep.toDto() = IngestionStepDto(
        id = id,
        stepName = stepName.name,
        status = status.name,
        startedAt = startedAt,
        completedAt = completedAt,
        recordsProcessed = recordsProcessed,
        recordsCreated = recordsCreated,
        recordsUpdated = recordsUpdated,
        recordsFailed = recordsFailed,
        errorCount = errors.size
    )

    private fun IngestionError.toDto() = IngestionErrorDto(
        id = id,
        stepId = step.id,
        errorType = errorType.name,
        errorCode = errorCode,
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}
