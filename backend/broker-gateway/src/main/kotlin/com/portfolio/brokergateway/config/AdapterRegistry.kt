package com.portfolio.brokergateway.config

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerUnsupportedOperationException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AdapterRegistry(
    adapters: List<BrokerAdapter>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val adapterMap: Map<BrokerType, BrokerAdapter> =
        adapters.associateBy { it.brokerType }

    @PostConstruct
    fun logRegisteredAdapters() {
        val registered = adapterMap.keys
        if (registered.isEmpty()) {
            log.warn("No broker adapters registered. Check @ConditionalOnProperty configuration and environment variable mappings.")
        } else {
            log.info("Registered broker adapters: {} ({} total)", registered.joinToString(", "), registered.size)
        }
    }

    fun getAdapter(brokerType: BrokerType): BrokerAdapter =
        adapterMap[brokerType]
            ?: throw BrokerUnsupportedOperationException(
                "No adapter registered for broker: $brokerType",
                brokerType
            )

    fun getEnabledBrokers(): List<BrokerType> {
        val enabled = adapterMap.keys.toList()
        log.debug("getEnabledBrokers() returning: {} ({} total)", enabled.joinToString(", "), enabled.size)
        return enabled
    }
}
