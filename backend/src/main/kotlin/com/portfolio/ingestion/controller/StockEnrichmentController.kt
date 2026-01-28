package com.portfolio.ingestion.controller

import com.portfolio.ingestion.service.IngestionOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for Alpha Vantage Stock enrichment (parsing stored raw payload).
 */
@RestController
@RequestMapping("/admin/enrichment/stocks")
class StockEnrichmentController(
    private val orchestrator: IngestionOrchestrator
) {
    /**
     * Trigger Alpha Vantage Stock enrichment only.
     * Parses the stored avRawPayload and maps fields to entity columns.
     * Does NOT call the API - raw data must already be ingested.
     *
     * POST /admin/enrichment/stocks/run
     */
    @PostMapping("/run")
    fun triggerStockEnrichment(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runStockEnrichmentOnly("api:/admin/enrichment/stocks/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Alpha Vantage Stock enrichment triggered successfully"
        ))
    }
}
