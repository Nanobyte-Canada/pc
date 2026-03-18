package com.portfolio.ingestion.controller

import com.portfolio.ingestion.service.IngestionOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for Stock Ingestion — builds the stock universe from EODHD.
 */
@RestController
@RequestMapping("/admin/ingestion/stocks")
class StockIngestionController(
    private val orchestrator: IngestionOrchestrator
) {
    /**
     * Trigger Stock Ingestion (EODHD universe refresh).
     * Fetches all exchange symbols from EODHD and inserts new stock records.
     *
     * POST /admin/ingestion/stocks/run
     */
    @PostMapping("/run")
    fun triggerStockIngestion(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runUniverseRefreshOnly("api:/admin/ingestion/stocks/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "Stock ingestion triggered successfully"
        ))
    }
}
