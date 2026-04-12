package com.portfolio.ingestion.provider

import org.springframework.stereotype.Component

@Component
class ProviderRegistry(providers: List<DataProvider>) {

    private val providerMap = providers.associateBy { it.name() }

    fun getProvider(name: String): DataProvider? = providerMap[name]

    fun getProvidersWithCapability(capability: ProviderCapability): List<DataProvider> =
        providerMap.values.filter { capability in it.capabilities() }

    fun allProviders(): Collection<DataProvider> = providerMap.values
}
