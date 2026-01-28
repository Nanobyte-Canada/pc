package com.portfolio.ingestion.controller

import com.portfolio.ingestion.service.IngestionOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for Alpha Vantage ETF enrichment (parsing stored raw payload).
 */
@RestController
@RequestMapping("/admin/enrichment/etfs")
class EtfEnrichmentController(
    private val orchestrator: IngestionOrchestrator
) {
    /**
     * Trigger Alpha Vantage ETF enrichment only.
     * Parses the stored avRawPayload and maps fields to entity columns.
     * Does NOT call the API - raw data must already be ingested.
     *
     * POST /admin/enrichment/etfs/run
     */
    @PostMapping("/run")
    fun triggerEtfEnrichment(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runEtfEnrichmentOnly("api:/admin/enrichment/etfs/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Alpha Vantage ETF enrichment triggered successfully"
        ))
    }
}
