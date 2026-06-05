package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.ibkr.IbkrConnectionManager
import com.portfolio.marketdata.ibkr.SubscriptionManager
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health")
class IbkrHealthController(
    private val connectionManager: IbkrConnectionManager,
    private val subscriptionManager: SubscriptionManager
) {

    @GetMapping("/ibkr")
    fun ibkrHealth(): Map<String, Any> {
        val state = connectionManager.getConnectionState()
        return mapOf(
            "connected" to (state == IbkrConnectionManager.ConnectionState.CONNECTED),
            "service" to "market-data",
            "connectionState" to state.name,
            "activeSubscriptions" to subscriptionManager.getActiveCount(),
            "pinnedSubscriptions" to subscriptionManager.getPinnedCount()
        )
    }
}
