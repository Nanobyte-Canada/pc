package com.portfolio.ingestion.controller

import com.portfolio.ingestion.service.IngestionOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for Alpha Vantage Stock ingestion (fetching raw data from API).
 */
@RestController
@RequestMapping("/admin/ingestion/stocks")
class StockIngestionController(
    private val orchestrator: IngestionOrchestrator
) {
    /**
     * Trigger Alpha Vantage Stock ingestion only.
     * Fetches raw data from AV OVERVIEW endpoint and stores in avRawPayload.
     * Does NOT parse/map fields - that's the enrichment step.
     *
     * POST /admin/ingestion/stocks/run
     */
    @PostMapping("/run")
    fun triggerStockIngestion(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runStockIngestionOnly("api:/admin/ingestion/stocks/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Alpha Vantage Stock ingestion triggered successfully"
        ))
    }
}
