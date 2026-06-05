package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.dto.CancelResult
import com.portfolio.brokergateway.adapter.dto.OrderImpactResult
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import com.portfolio.brokergateway.adapter.dto.OrderResult
import com.portfolio.brokergateway.api.dto.PlaceOrderRequest
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gateway/connections/{connectionId}/accounts/{accountId}/orders")
class OrderController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun placeOrder(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestBody request: PlaceOrderRequest
    ): ResponseEntity<OrderResult> {
        val orderRequest = OrderRequest(
            symbol = request.symbol, action = request.action, quantity = request.quantity,
            orderType = request.orderType, limitPrice = request.limitPrice, stopPrice = request.stopPrice,
            timeInForce = request.timeInForce, currency = request.currency,
            symbolId = request.symbolId, primaryRoute = request.primaryRoute, secondaryRoute = request.secondaryRoute,
            optionType = request.optionType, strike = request.strike, expiry = request.expiry
        )
        val result = withAutoRetry(connectionId) { adapter, creds -> adapter.placeOrder(creds, accountId, orderRequest) }
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/{brokerOrderId}")
    fun cancelOrder(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @PathVariable brokerOrderId: String
    ): ResponseEntity<CancelResult> {
        val result = withAutoRetry(connectionId) { adapter, creds -> adapter.cancelOrder(creds, accountId, brokerOrderId) }
        return ResponseEntity.ok(result)
    }

    @PostMapping("/impact")
    fun getOrderImpact(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestBody request: PlaceOrderRequest
    ): ResponseEntity<OrderImpactResult> {
        val orderRequest = OrderRequest(
            symbol = request.symbol, action = request.action, quantity = request.quantity,
            orderType = request.orderType, limitPrice = request.limitPrice, stopPrice = request.stopPrice,
            timeInForce = request.timeInForce, currency = request.currency
        )
        val result = withAutoRetry(connectionId) { adapter, creds -> adapter.getOrderImpact(creds, accountId, orderRequest) }
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
