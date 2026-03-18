package com.portfolio.ingestion.controller

import com.portfolio.entity.AVIngestionStatus
import com.portfolio.entity.EtfComEnrichmentStatus
import com.portfolio.ingestion.entity.IngestionError
import com.portfolio.ingestion.entity.IngestionRun
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.repository.IngestionErrorRepository
import com.portfolio.ingestion.repository.IngestionRunRepository
import com.portfolio.ingestion.service.IngestionOrchestrator
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.StockRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

// ─────────────────────────────────────────────
// DTOs
// ─────────────────────────────────────────────

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

data class ErrorSummaryDto(
    val errorType: String,
    val count: Long,
    val lastOccurredAt: OffsetDateTime?
)

data class IngestionStatsDto(
    val totalStocks: Long,
    val stocksWithRawData: Long,
    val stocksPendingIngestion: Long,
    val totalEtfs: Long,
    val etfsEnriched: Long,
    val etfsPendingEnrichment: Long,
    val errorsLast24h: Long
)

data class TriggerIngestionRequest(
    val steps: List<String>? = null
)

data class TriggerIngestionResponse(
    val runId: Long,
    val status: String,
    val message: String
)

// ─────────────────────────────────────────────
// Controller
// ─────────────────────────────────────────────

@RestController
@RequestMapping("/admin/ingestion")
class AdminIngestionController(
    private val orchestrator: IngestionOrchestrator,
    private val trackingService: IngestionTrackingService,
    private val runRepository: IngestionRunRepository,
    private val errorRepository: IngestionErrorRepository,
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository
) {

    /** POST /admin/ingestion/run — full pipeline */
    @PostMapping("/run")
    fun triggerIngestion(
        @RequestBody(required = false) request: TriggerIngestionRequest?
    ): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runFullIngestion("api:/admin/ingestion/run")
        return ResponseEntity.ok(TriggerIngestionResponse(run.id, run.status.name, "Full ingestion run triggered successfully"))
    }

    /** GET /admin/ingestion/runs?limit=10 */
    @GetMapping("/runs")
    fun getRecentRuns(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<List<IngestionRunDto>> {
        val runs = trackingService.getRecentRuns(limit)
        return ResponseEntity.ok(runs.map { it.toDto() })
    }

    /** GET /admin/ingestion/runs/{id} */
    @GetMapping("/runs/{id}")
    fun getRunDetails(@PathVariable id: Long): ResponseEntity<IngestionRunDetailDto> {
        val run = trackingService.getRunWithDetails(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(run.toDetailDto())
    }

    /** GET /admin/ingestion/runs/{id}/steps — lazy-loadable step breakdown for a run */
    @GetMapping("/runs/{id}/steps")
    fun getRunSteps(@PathVariable id: Long): ResponseEntity<List<IngestionStepDto>> {
        val run = trackingService.getRunWithDetails(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(run.steps.map { it.toDto() })
    }

    /** GET /admin/ingestion/runs/{id}/errors */
    @GetMapping("/runs/{id}/errors")
    fun getRunErrors(@PathVariable id: Long): ResponseEntity<List<IngestionErrorDto>> {
        val errors = errorRepository.findByRunId(id)
        return ResponseEntity.ok(errors.map { it.toDto() })
    }

    /**
     * GET /admin/ingestion/errors?stepName=X&errorType=Y&limit=100
     * Filterable errors endpoint with optional query params.
     */
    @GetMapping("/errors")
    fun getRecentErrors(
        @RequestParam(required = false) stepName: String?,
        @RequestParam(required = false) errorType: String?,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<IngestionErrorDto>> {
        val errors = if (stepName == null && errorType == null) {
            errorRepository.findRecentErrors(PageRequest.of(0, limit))
        } else {
            errorRepository.findFilteredErrors(stepName, errorType, PageRequest.of(0, limit))
        }
        return ResponseEntity.ok(errors.map { it.toDto() })
    }

    /**
     * GET /admin/ingestion/errors/summary
     * Error counts grouped by errorType, last 24 hours.
     */
    @GetMapping("/errors/summary")
    fun getErrorSummary(): ResponseEntity<List<ErrorSummaryDto>> {
        val since = OffsetDateTime.now().minusHours(24)
        val raw = errorRepository.getErrorSummaryRaw(since)
        val summary = raw.map { row ->
            ErrorSummaryDto(
                errorType = row[0].toString(),
                count = (row[1] as Number).toLong(),
                lastOccurredAt = row[2]?.let { it as? OffsetDateTime }
            )
        }
        return ResponseEntity.ok(summary)
    }

    /**
     * GET /admin/ingestion/stats
     * Aggregate pipeline statistics across stocks and ETFs.
     */
    @GetMapping("/stats")
    fun getIngestionStats(): ResponseEntity<IngestionStatsDto> {
        val since = OffsetDateTime.now().minusHours(24)

        return ResponseEntity.ok(IngestionStatsDto(
            totalStocks = stockRepository.count(),
            stocksWithRawData = stockRepository.countByAvIngestionStatus(AVIngestionStatus.SUCCESS),
            stocksPendingIngestion = stockRepository.countAvIngestionPending(),
            totalEtfs = etfRepository.count(),
            etfsEnriched = etfRepository.countByEtfcomEnrichmentStatus(EtfComEnrichmentStatus.SUCCESS),
            etfsPendingEnrichment = etfRepository.countEtfsPendingEnrichment(),
            errorsLast24h = errorRepository.countErrorsSince(since)
        ))
    }

    // ─────────────────────────────────────────────
    // DTO conversions
    // ─────────────────────────────────────────────

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
