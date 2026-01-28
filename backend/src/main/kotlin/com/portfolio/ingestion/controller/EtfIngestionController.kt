package com.portfolio.ingestion.controller

import com.portfolio.ingestion.service.IngestionOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for Alpha Vantage ETF ingestion (fetching raw data from API).
 */
@RestController
@RequestMapping("/admin/ingestion/etfs")
class EtfIngestionController(
    private val orchestrator: IngestionOrchestrator
) {
    /**
     * Trigger Alpha Vantage ETF ingestion only.
     * Fetches raw data from AV ETF_PROFILE endpoint and stores in avRawPayload.
     * Does NOT parse/map fields - that's the enrichment step.
     *
     * POST /admin/ingestion/etfs/run
     */
    @PostMapping("/run")
    fun triggerEtfIngestion(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runEtfIngestionOnly("api:/admin/ingestion/etfs/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Alpha Vantage ETF ingestion triggered successfully"
        ))
    }
}
