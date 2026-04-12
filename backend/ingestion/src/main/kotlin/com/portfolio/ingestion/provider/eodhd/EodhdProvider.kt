package com.portfolio.ingestion.provider.eodhd

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.ingestion.provider.*
import org.springframework.stereotype.Component

@Component
class EodhdProvider(
    private val client: EodhdClient
) : DataProvider {

    override fun name(): String = "EODHD"

    override fun capabilities(): Set<ProviderCapability> = setOf(
        ProviderCapability.EXCHANGES,
        ProviderCapability.UNIVERSE,
        ProviderCapability.FUNDAMENTALS
    )

    override suspend fun fetchExchanges(): List<RawExchange> =
        client.fetchExchanges().map { dto ->
            RawExchange(
                code = dto.code,
                name = dto.name,
                country = dto.country,
                currency = dto.currency,
                operatingMic = dto.operatingMic
            )
        }

    override suspend fun fetchUniverse(exchange: String): List<RawInstrument> =
        client.fetchSymbols(exchange).mapNotNull { dto ->
            if (dto.name.isNullOrBlank()) return@mapNotNull null
            RawInstrument(
                ticker = dto.code,
                name = dto.name,
                type = dto.type ?: "Unknown",
                exchange = exchange,
                currency = dto.currency,
                country = dto.country,
                isin = dto.isin?.takeIf { it.isNotBlank() && it.length == 12 }
            )
        }

    override suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode? =
        client.fetchFundamentals(ticker, exchange)
}
