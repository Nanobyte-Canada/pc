package com.portfolio.brokergateway.config

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerUnsupportedOperationException
import org.springframework.stereotype.Component

@Component
class AdapterRegistry(
    adapters: List<BrokerAdapter>
) {
    private val adapterMap: Map<BrokerType, BrokerAdapter> =
        adapters.associateBy { it.brokerType }

    fun getAdapter(brokerType: BrokerType): BrokerAdapter =
        adapterMap[brokerType]
            ?: throw BrokerUnsupportedOperationException(
                "No adapter registered for broker: $brokerType",
                brokerType
            )

    fun getEnabledBrokers(): List<BrokerType> = adapterMap.keys.toList()
}
