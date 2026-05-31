package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.dto.*
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
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
        val accounts = withAutoRetry(connectionId) { adapter, creds -> adapter.listAccounts(creds) }
        return ResponseEntity.ok(mapOf("accounts" to accounts))
    }

    @GetMapping("/{accountId}/balances")
    fun getBalances(
        @PathVariable connectionId: String,
        @PathVariable accountId: String
    ): ResponseEntity<UnifiedBalance> {
        return ResponseEntity.ok(withAutoRetry(connectionId) { adapter, creds -> adapter.getBalances(creds, accountId) })
    }

    @GetMapping("/{accountId}/positions")
    fun getPositions(
        @PathVariable connectionId: String,
        @PathVariable accountId: String
    ): ResponseEntity<Map<String, List<UnifiedPosition>>> {
        val positions = withAutoRetry(connectionId) { adapter, creds -> adapter.getPositions(creds, accountId) }
        return ResponseEntity.ok(mapOf("positions" to positions))
    }

    @GetMapping("/{accountId}/activities")
    fun getActivities(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<Map<String, List<UnifiedActivity>>> {
        val activities = withAutoRetry(connectionId) { adapter, creds -> adapter.getActivities(creds, accountId, startDate, endDate) }
        return ResponseEntity.ok(mapOf("activities" to activities))
    }

    @GetMapping("/{accountId}/orders")
    fun getOrders(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<Map<String, List<UnifiedOrder>>> {
        val orders = withAutoRetry(connectionId) { adapter, creds -> adapter.getOrders(creds, accountId) }
        return ResponseEntity.ok(mapOf("orders" to orders))
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private fun <T> withAutoRetry(connectionId: String, operation: (BrokerAdapter, BrokerCredentials) -> T): T {
        val rawCredentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(rawCredentials.brokerType)
        val credentials = credentialService.getCredentialsWithRefresh(connectionId, adapter)
        return try {
            operation(adapter, credentials)
        } catch (e: BrokerAuthenticationException) {
            log.warn("401 from broker for connection {}, force-refreshing and retrying...", connectionId)
            val refreshed = credentialService.forceRefresh(connectionId, adapter)
            operation(adapter, refreshed)
        }
    }
}
