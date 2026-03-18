package com.portfolio.ingestion.controller

import com.portfolio.ingestion.service.IngestionOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for Stock Enrichment — fetches raw data from Alpha Vantage.
 */
@RestController
@RequestMapping("/admin/enrichment/stocks")
class StockEnrichmentController(
    private val orchestrator: IngestionOrchestrator
) {
    /**
     * Trigger Stock Enrichment (Alpha Vantage raw data fetch).
     * Fetches OVERVIEW data from Alpha Vantage and stores raw JSON payload.
     *
     * POST /admin/enrichment/stocks/run
     */
    @PostMapping("/run")
    fun triggerStockEnrichment(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runStockIngestionOnly("api:/admin/enrichment/stocks/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Stock enrichment triggered successfully"
        ))
    }
}
