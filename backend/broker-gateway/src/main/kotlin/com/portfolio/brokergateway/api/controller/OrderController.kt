package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.dto.CancelResult
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import com.portfolio.brokergateway.adapter.dto.OrderResult
import com.portfolio.brokergateway.api.dto.PlaceOrderRequest
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gateway/connections/{connectionId}/accounts/{accountId}/orders")
class OrderController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    @PostMapping
    fun placeOrder(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestBody request: PlaceOrderRequest
    ): ResponseEntity<OrderResult> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val orderRequest = OrderRequest(
            symbol = request.symbol,
            action = request.action,
            quantity = request.quantity,
            orderType = request.orderType,
            limitPrice = request.limitPrice,
            stopPrice = request.stopPrice,
            timeInForce = request.timeInForce,
            currency = request.currency
        )
        val result = adapter.placeOrder(credentials, accountId, orderRequest)
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/{brokerOrderId}")
    fun cancelOrder(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @PathVariable brokerOrderId: String
    ): ResponseEntity<CancelResult> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val result = adapter.cancelOrder(credentials, accountId, brokerOrderId)
        return ResponseEntity.ok(result)
    }
}
