package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.dto.*
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/gateway/connections/{connectionId}/accounts")
class DataController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    @GetMapping
    fun listAccounts(@PathVariable connectionId: String): ResponseEntity<Map<String, List<UnifiedAccount>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val accounts = adapter.listAccounts(credentials)
        return ResponseEntity.ok(mapOf("accounts" to accounts))
    }

    @GetMapping("/{accountId}/balances")
    fun getBalances(
        @PathVariable connectionId: String,
        @PathVariable accountId: String
    ): ResponseEntity<UnifiedBalance> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(adapter.getBalances(credentials, accountId))
    }

    @GetMapping("/{accountId}/positions")
    fun getPositions(
        @PathVariable connectionId: String,
        @PathVariable accountId: String
    ): ResponseEntity<Map<String, List<UnifiedPosition>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(mapOf("positions" to adapter.getPositions(credentials, accountId)))
    }

    @GetMapping("/{accountId}/activities")
    fun getActivities(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<Map<String, List<UnifiedActivity>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(mapOf("activities" to adapter.getActivities(credentials, accountId, startDate, endDate)))
    }

    @GetMapping("/{accountId}/orders")
    fun getOrders(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<Map<String, List<UnifiedOrder>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(mapOf("orders" to adapter.getOrders(credentials, accountId)))
    }
}
