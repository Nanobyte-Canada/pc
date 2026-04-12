package com.portfolio.ingestion.provider.eodhd

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.ingestion.config.IngestionProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class EodhdClient(
    private val eodhdWebClient: WebClient,
    private val props: IngestionProperties,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val apiKey = props.eodhd.apiKey
    private val latencyTimer = Timer.builder("eodhd_api_latency_seconds").register(meterRegistry)

    suspend fun fetchExchanges(): List<EodhdExchangeDto> {
        log.debug("Fetching exchange list from EODHD")
        return eodhdWebClient.get()
            .uri("/exchanges-list/?api_token=$apiKey&fmt=json")
            .retrieve()
            .awaitBody()
    }

    suspend fun fetchSymbols(exchange: String): List<EodhdSymbolDto> {
        log.debug("Fetching symbols for exchange: {}", exchange)
        return eodhdWebClient.get()
            .uri("/exchange-symbol-list/$exchange?api_token=$apiKey&fmt=json")
            .retrieve()
            .awaitBody()
    }

    suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode {
        log.debug("Fetching fundamentals for {}.{}", ticker, exchange)
        return eodhdWebClient.get()
            .uri("/fundamentals/$ticker.$exchange?api_token=$apiKey&fmt=json")
            .retrieve()
            .awaitBody()
    }
}
