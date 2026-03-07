package com.portfolio.ingestion.controller

import com.portfolio.ingestion.service.IngestionOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class EtfComController(
    private val orchestrator: IngestionOrchestrator
) {
    @PostMapping("/ingestion/etfcom/universe")
    fun triggerEtfComUniverse(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runEtfComUniverseOnly("api:/admin/ingestion/etfcom/universe")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "etf.com ETF universe refresh triggered successfully"
        ))
    }

    @PostMapping("/enrichment/etfcom/run")
    fun triggerEtfComEnrichment(): ResponseEntity<TriggerIngestionResponse> {
        val run = orchestrator.runEtfComEnrichmentOnly("api:/admin/enrichment/etfcom/run")

        return ResponseEntity.ok(TriggerIngestionResponse(
            runId = run.id,
            status = run.status.name,
            message = "etf.com ETF enrichment triggered successfully"
        ))
    }
}
