package com.portfolio.marketdata.questrade

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriUtils
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class QuestradeSymbol(
    val symbolId: Int,
    val symbol: String,
    val description: String,
    val securityType: String,
    val listingExchange: String?,
    val isQuotable: Boolean,
    val currency: String?,
    val hasOptions: Boolean
)

data class QuestradeStrike(val strikePrice: Double, val callSymbolId: Int, val putSymbolId: Int)

data class QuestradeRoot(val optionRoot: String, val multiplier: Int, val chainPerStrikePrice: List<QuestradeStrike>)

data class QuestradeExpiry(
    val expiryDate: String,
    val optionExerciseType: String?,
    val listingExchange: String?,
    val chainPerRoot: List<QuestradeRoot>
)

data class QuestradeOptionQuote(
    val symbolId: Int,
    val underlyingId: Int,
    val bidPrice: Double?,
    val askPrice: Double?,
    val lastTradePrice: Double?,
    val volume: Long,
    val openInterest: Long,
    val volatility: Double?,
    val delta: Double?,
    val gamma: Double?,
    val theta: Double?,
    val vega: Double?,
    val rho: Double?,
    val delay: Int
)

data class QuestradeStockQuote(
    val symbolId: Int,
    val bidPrice: Double?,
    val askPrice: Double?,
    val lastTradePrice: Double?,
    val volume: Long,
    val delay: Int
)

/** Thrown when a Questrade API response body signals an invalid access token (code 1017). */
class RestCallFailedWith1017 : RuntimeException()

@Component
class QuestradeRestClient(
    private val tokenManager: QuestradeTokenManager,
    builder: RestClient.Builder = RestClient.builder()
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = builder.build()
    private val objectMapper = ObjectMapper()

    fun searchSymbol(prefix: String): List<QuestradeSymbol> = withApi { base ->
        val encoded = UriUtils.encodeQueryParam(prefix, Charsets.UTF_8)
        val node = fetchJson(HttpMethod.GET, "$base/v1/symbols/search?prefix=$encoded")
        node.path("symbols").map { toSymbol(it) }
    }

    fun getOptionChain(underlyingId: Int): List<QuestradeExpiry> = withApi { base ->
        val node = fetchJson(HttpMethod.GET, "$base/v1/symbols/$underlyingId/options")
        node.path("optionChain").map { expiry ->
            QuestradeExpiry(
                expiryDate = expiry.path("expiryDate").asText(),
                optionExerciseType = expiry.path("optionExerciseType").asTextOrNull(),
                listingExchange = expiry.path("listingExchange").asTextOrNull(),
                chainPerRoot = expiry.path("chainPerRoot").map { root ->
                    QuestradeRoot(
                        optionRoot = root.path("optionRoot").asText(),
                        multiplier = root.path("multiplier").asInt(0),
                        chainPerStrikePrice = root.path("chainPerStrikePrice").map { strike ->
                            QuestradeStrike(
                                strikePrice = strike.path("strikePrice").asDouble(0.0),
                                callSymbolId = strike.path("callSymbolId").asInt(0),
                                putSymbolId = strike.path("putSymbolId").asInt(0)
                            )
                        }
                    )
                }
            )
        }
    }

    fun getOptionQuotes(optionIds: List<Int>): List<QuestradeOptionQuote> =
        optionIds.chunked(OPTION_QUOTE_CHUNK_SIZE).flatMap { chunk ->
            withApi { base ->
                val body = "{\"optionIds\":[${chunk.joinToString(",")}]}"
                val node = fetchJson(HttpMethod.POST, "$base/v1/markets/quotes/options", body)
                node.path("optionQuotes").map { toOptionQuote(it) }
            }
        }

    fun getStockQuotes(symbolIds: List<Int>): List<QuestradeStockQuote> = withApi { base ->
        val node = fetchJson(HttpMethod.GET, "$base/v1/markets/quotes?ids=${symbolIds.joinToString(",")}")
        node.path("quotes").map { quote ->
            QuestradeStockQuote(
                symbolId = quote.path("symbolId").asInt(0),
                bidPrice = quote.path("bidPrice").asDoubleOrNull(),
                askPrice = quote.path("askPrice").asDoubleOrNull(),
                lastTradePrice = quote.path("lastTradePrice").asDoubleOrNull(),
                volume = quote.path("volume").asLong(0),
                delay = quote.path("delay").asInt(0)
            )
        }
    }

    fun negotiateStockStream(symbolIds: List<Int>): Int = withApi { base ->
        val url = "$base/v1/markets/stream?stream=true&mode=WebSocket&symbolIds=${symbolIds.joinToString(",")}"
        fetchJson(HttpMethod.GET, url).path("streamPort").asInt(-1)
    }

    fun negotiateOptionStream(optionIds: List<Int>): Int = withApi { base ->
        val body = "{\"stream\":true,\"mode\":\"WebSocket\",\"optionIds\":[${optionIds.joinToString(",")}]}"
        fetchJson(HttpMethod.POST, "$base/v1/markets/stream", body).path("streamPort").asInt(-1)
    }

    fun serverTime(): Instant = withApi { base ->
        val node = fetchJson(HttpMethod.GET, "$base/v1/time")
        parseTimestamp(node.path("time").asText())
    }

    /**
     * Runs [block] against the current access token's API server; retries ONCE with a
     * freshly exchanged token when the call fails with Questrade error code 1017.
     */
    private inline fun <T> withApi(crossinline block: (String) -> T): T {
        val token = tokenManager.getValidAccessToken()
        return try {
            block(token.apiServer.trimEnd('/'))
        } catch (e: RestCallFailedWith1017) {
            log.warn("Questrade API rejected access token (1017); forcing refresh and retrying once")
            val fresh = tokenManager.forceRefresh()
            block(fresh.apiServer.trimEnd('/'))
        }
    }

    /** Executes the request and parses the JSON body, detecting the 1017 invalid-token envelope. */
    private fun fetchJson(method: HttpMethod, url: String, jsonBody: String? = null): JsonNode {
        val raw = executeRaw(method, url, jsonBody)
        if (raw.contains("\"code\":1017")) throw RestCallFailedWith1017()
        return objectMapper.readTree(raw)
    }

    /** Executes the request, retrying once after sleeping when HTTP 429 is returned. */
    private fun executeRaw(method: HttpMethod, url: String, jsonBody: String?): String {
        repeat(2) { attempt ->
            try {
                return doExecute(method, url, jsonBody)
            } catch (e: RestClientResponseException) {
                if (e.statusCode.value() == 429 && attempt == 0) {
                    sleepForRateLimit(e)
                } else {
                    throw e
                }
            }
        }
        throw IllegalStateException("unreachable")
    }

    private fun doExecute(method: HttpMethod, url: String, jsonBody: String?): String {
        val spec = when (method) {
            HttpMethod.GET -> restClient.get().uri(url)
            HttpMethod.POST -> {
                val post = restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                if (jsonBody != null) post.body(jsonBody) else post
            }
            else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
        }
        return spec.retrieve().body(String::class.java) ?: ""
    }

    /** Sleeps until the rate-limit window resets per X-RateLimit-Reset, capped at 60s. */
    private fun sleepForRateLimit(e: RestClientResponseException) {
        val resetHeader = e.responseHeaders?.getFirst("X-RateLimit-Reset")
        val delaySeconds = resetHeader?.toLongOrNull()
            ?.let { reset -> (reset - System.currentTimeMillis() / 1000).coerceIn(0, MAX_RATE_LIMIT_SLEEP_SECONDS) }
            ?: DEFAULT_RATE_LIMIT_SLEEP_SECONDS
        log.warn("Questrade rate limited (429); sleeping {}s before retry", delaySeconds)
        Thread.sleep(delaySeconds * 1000)
    }

    private fun toSymbol(n: JsonNode) = QuestradeSymbol(
        symbolId = n.path("symbolId").asInt(0),
        symbol = n.path("symbol").asText(),
        description = n.path("description").asText(),
        securityType = n.path("securityType").asText(),
        listingExchange = n.path("listingExchange").asTextOrNull(),
        isQuotable = n.path("isQuotable").asBoolean(false),
        currency = n.path("currency").asTextOrNull(),
        hasOptions = n.path("hasOptions").asBoolean(false)
    )

    private fun toOptionQuote(n: JsonNode) = QuestradeOptionQuote(
        symbolId = n.path("symbolId").asInt(0),
        underlyingId = n.path("underlyingId").asInt(0),
        bidPrice = n.path("bidPrice").asDoubleOrNull(),
        askPrice = n.path("askPrice").asDoubleOrNull(),
        lastTradePrice = n.path("lastTradePrice").asDoubleOrNull(),
        volume = n.path("volume").asLong(0),
        openInterest = n.path("openInterest").asLong(0),
        volatility = n.path("volatility").asDoubleOrNull(),
        delta = n.path("delta").asDoubleOrNull(),
        gamma = n.path("gamma").asDoubleOrNull(),
        theta = n.path("theta").asDoubleOrNull(),
        vega = n.path("vega").asDoubleOrNull(),
        rho = n.path("rho").asDoubleOrNull(),
        delay = n.path("delay").asInt(0)
    )

    private fun parseTimestamp(raw: String): Instant =
        try {
            OffsetDateTime.parse(raw).toInstant()
        } catch (_: DateTimeParseException) {
            Instant.parse(raw)
        }

    private fun JsonNode.asTextOrNull(): String? = takeIf { !isNull && !isMissingNode }?.asText()

    private fun JsonNode.asDoubleOrNull(): Double? =
        takeIf { !isNull && !isMissingNode && isNumber }?.asDouble()

    companion object {
        /** Questrade caps option-quote batches at 100 ids per POST. */
        const val OPTION_QUOTE_CHUNK_SIZE = 100
        private const val MAX_RATE_LIMIT_SLEEP_SECONDS = 60L
        private const val DEFAULT_RATE_LIMIT_SLEEP_SECONDS = 1L
    }
}
