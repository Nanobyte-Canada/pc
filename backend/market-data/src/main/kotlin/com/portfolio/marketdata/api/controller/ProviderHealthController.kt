package com.portfolio.marketdata.api.controller

import com.portfolio.marketdata.provider.MarketDataProvider
import com.portfolio.marketdata.provider.SubscriptionManager
import com.portfolio.marketdata.questrade.QuestradeConnectionManager
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health")
class ProviderHealthController(
    private val connectionManager: QuestradeConnectionManager,
    private val subscriptionManager: SubscriptionManager,
    private val provider: MarketDataProvider
) {

    @GetMapping("/provider")
    fun providerHealth(): Map<String, Any> = healthPayload()

    /** Temporary alias for frontend compatibility; removed in Task 13. */
    @GetMapping("/ibkr")
    fun ibkrHealthAlias(): Map<String, Any> = healthPayload()

    private fun healthPayload(): Map<String, Any> {
        val state = connectionManager.getConnectionState()
        return mapOf(
            "connected" to (state == QuestradeConnectionManager.ConnectionState.CONNECTED),
            "provider" to "questrade",
            "connectionState" to state.name,
            "activeSubscriptions" to subscriptionManager.getActiveCount(),
            "pinnedSubscriptions" to subscriptionManager.getPinnedCount()
        )
    }
}
