package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.api.dto.BrokerHealthResponse
import com.portfolio.brokergateway.api.dto.GatewayHealthResponse
import com.portfolio.brokergateway.config.AdapterRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/gateway/health")
class HealthController(
    private val adapterRegistry: AdapterRegistry
) {
    @GetMapping
    fun health(): ResponseEntity<GatewayHealthResponse> {
        val enabledBrokers = adapterRegistry.getEnabledBrokers()
        val brokerStatuses = BrokerType.entries.map { type ->
            BrokerHealthResponse(
                brokerType = type,
                enabled = type in enabledBrokers,
                status = if (type in enabledBrokers) "OK" else "DISABLED"
            )
        }
        return ResponseEntity.ok(GatewayHealthResponse(status = "UP", brokers = brokerStatuses))
    }

    @GetMapping("/{brokerType}")
    fun brokerHealth(@PathVariable brokerType: BrokerType): ResponseEntity<BrokerHealthResponse> {
        val enabledBrokers = adapterRegistry.getEnabledBrokers()
        val enabled = brokerType in enabledBrokers
        return ResponseEntity.ok(
            BrokerHealthResponse(
                brokerType = brokerType,
                enabled = enabled,
                status = if (enabled) "OK" else "DISABLED"
            )
        )
    }
}
