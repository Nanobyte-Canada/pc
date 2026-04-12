package com.portfolio.ingestion.provider

import com.fasterxml.jackson.databind.JsonNode

data class RawExchange(
    val code: String,
    val name: String,
    val country: String?,
    val currency: String?,
    val operatingMic: String?
)

data class RawInstrument(
    val ticker: String,
    val name: String,
    val type: String,
    val exchange: String,
    val currency: String?,
    val country: String?,
    val isin: String?
)

interface DataProvider {
    fun name(): String
    fun capabilities(): Set<ProviderCapability>
    suspend fun fetchExchanges(): List<RawExchange>
    suspend fun fetchUniverse(exchange: String): List<RawInstrument>
    suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode?
}
