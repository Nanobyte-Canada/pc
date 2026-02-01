package com.portfolio.broker.client

import org.springframework.stereotype.Component

/**
 * Factory for creating broker-specific API clients.
 * Returns the appropriate client implementation based on broker code.
 */
@Component
class BrokerClientFactory(
    private val clients: List<BrokerClient>
) {
    private val clientMap: Map<String, BrokerClient> by lazy {
        clients.associateBy { it.brokerCode.uppercase() }
    }

    /**
     * Get the broker client for the specified broker code.
     *
     * @param brokerCode The broker code (e.g., "QUESTRADE", "IBKR", "WEALTHSIMPLE")
     * @return The appropriate BrokerClient implementation
     * @throws BrokerNotSupportedException if no client exists for the broker
     */
    fun getClient(brokerCode: String): BrokerClient {
        return clientMap[brokerCode.uppercase()]
            ?: throw BrokerNotSupportedException("No client implementation for broker: $brokerCode")
    }

    /**
     * Check if a client exists for the specified broker.
     */
    fun hasClient(brokerCode: String): Boolean {
        return clientMap.containsKey(brokerCode.uppercase())
    }

    /**
     * Get all supported broker codes.
     */
    fun getSupportedBrokers(): Set<String> {
        return clientMap.keys
    }
}

class BrokerNotSupportedException(message: String) : RuntimeException(message)
