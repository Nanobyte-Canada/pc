package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.dto.MultiLegOrderRequest
import com.portfolio.brokergateway.adapter.dto.OrderResult
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gateway/connections/{connectionId}/accounts/{accountId}/combo-orders")
class ComboOrderController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun placeComboOrder(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestBody request: MultiLegOrderRequest
    ): ResponseEntity<OrderResult> {
        val result = withAutoRetry(connectionId) { adapter, creds ->
            adapter.placeMultiLegOrder(creds, accountId, request)
        }
        return ResponseEntity.ok(result)
    }

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
