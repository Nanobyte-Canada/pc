package com.portfolio.ingestion.client

import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.dto.eodhd.EodhdFundamentalsDto
import com.portfolio.ingestion.dto.eodhd.EodhdInstrumentDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class EodhdClient(
    @Qualifier("eodhdWebClient") private val webClient: WebClient,
    private val config: IngestionConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val rateLimitDelay = Duration.ofMillis(1000L / config.eodhd.rateLimitPerSecond)

    /**
     * Fetches all symbols for a given exchange
     * GET /api/exchange-symbol-list/{EXCHANGE}?api_token={API_KEY}&fmt=json
     */
    fun getExchangeSymbols(exchange: String): List<EodhdInstrumentDto> {
        log.info("Fetching symbols for exchange: $exchange")

        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/exchange-symbol-list/$exchange")
                    .queryParam("api_token", config.eodhd.apiKey)
                    .queryParam("fmt", "json")
                    .build()
            }
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<EodhdInstrumentDto>>() {})
            .delayElement(rateLimitDelay)
            .doOnError { e ->
                log.error("Error fetching symbols for exchange $exchange: ${e.message}")
            }
            .onErrorReturn(emptyList())
            .block() ?: emptyList()
    }

    /**
     * Fetches fundamentals data for a specific instrument
     * GET /api/fundamentals/{SYMBOL}.{EXCHANGE}?api_token={API_KEY}
     */
    fun getFundamentals(symbol: String, exchange: String): EodhdFundamentalsDto? {
        log.debug("Fetching fundamentals for $symbol.$exchange")

        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/fundamentals/$symbol.$exchange")
                    .queryParam("api_token", config.eodhd.apiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(EodhdFundamentalsDto::class.java)
            .delayElement(rateLimitDelay)
            .onErrorResume(WebClientResponseException::class.java) { e ->
                log.warn("Error fetching fundamentals for $symbol.$exchange: ${e.statusCode}")
                Mono.empty()
            }
            .block()
    }

    /**
     * Fetches symbols with rate limiting (blocking version for batch processing)
     */
    fun getExchangeSymbolsBlocking(exchange: String): List<EodhdInstrumentDto> {
        Thread.sleep(rateLimitDelay.toMillis())
        return getExchangeSymbols(exchange)
    }
}
