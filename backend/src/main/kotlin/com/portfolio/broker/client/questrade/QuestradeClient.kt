package com.portfolio.broker.client.questrade

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.client.*
import com.portfolio.broker.config.BrokerConfig
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Questrade API client implementation.
 *
 * Questrade uses OAuth 2.0 with some unique characteristics:
 * - Refresh tokens are single-use (new token returned on each refresh)
 * - Token response includes api_server URL that must be used for API calls
 * - Rate limit: typically 1 request per second
 */
@Component
class QuestradeClient(
    private val config: BrokerConfig,
    private val rateLimiter: QuestradeRateLimiter,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val questradeWebClient: WebClient
) : BrokerClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override val brokerCode = "QUESTRADE"

    // Metrics
    private val requestsTotal = Counter.builder("questrade_api_requests_total")
        .description("Total Questrade API requests")
        .register(meterRegistry)

    private val successTotal = Counter.builder("questrade_api_success_total")
        .description("Successful Questrade API responses")
        .register(meterRegistry)

    private val errorTotal = Counter.builder("questrade_api_error_total")
        .description("Failed Questrade API responses")
        .register(meterRegistry)

    private val apiLatency = Timer.builder("questrade_api_latency_seconds")
        .description("Questrade API request latency")
        .register(meterRegistry)

    override suspend fun getAuthorizationUrl(
        redirectUri: String,
        state: String,
        codeVerifier: String?
    ): String {
        return UriComponentsBuilder.fromUriString(config.questrade.authorizationUrl)
            .queryParam("client_id", config.questrade.clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", state)
            .build()
            .toUriString()
    }

    override suspend fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        codeVerifier: String?
    ): BrokerTokenResponse {
        rateLimiter.acquire()
        requestsTotal.increment()

        val startTime = System.nanoTime()

        try {
            val response = questradeWebClient.post()
                .uri(config.questrade.tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(
                    "grant_type=authorization_code" +
                    "&code=$code" +
                    "&redirect_uri=$redirectUri" +
                    "&client_id=${config.questrade.clientId}"
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { clientResponse ->
                    handleErrorResponse(clientResponse.statusCode(), "Token exchange")
                }
                .bodyToMono(QuestradeTokenResponse::class.java)
                .awaitSingleOrNull()
                ?: throw BrokerApiException("Empty response from Questrade token exchange")

            successTotal.increment()
            recordLatency(startTime)

            log.info("Successfully exchanged code for tokens, api_server: {}", response.apiServer)

            return BrokerTokenResponse(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                tokenType = response.tokenType,
                expiresIn = response.expiresIn?.toLong(),
                apiServerUrl = response.apiServer
            )
        } catch (e: BrokerException) {
            errorTotal.increment()
            throw e
        } catch (e: Exception) {
            errorTotal.increment()
            log.error("Token exchange failed: ${e.message}", e)
            throw BrokerApiException("Token exchange failed: ${e.message}", cause = e)
        }
    }

    override suspend fun refreshAccessToken(refreshToken: String): BrokerTokenResponse {
        rateLimiter.acquire()
        requestsTotal.increment()

        val startTime = System.nanoTime()

        try {
            // Questrade refresh uses the same token endpoint
            val response = questradeWebClient.post()
                .uri(config.questrade.tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(
                    "grant_type=refresh_token" +
                    "&refresh_token=$refreshToken"
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { clientResponse ->
                    when (clientResponse.statusCode()) {
                        HttpStatus.UNAUTHORIZED, HttpStatus.BAD_REQUEST -> {
                            Mono.error(BrokerTokenExpiredException("Refresh token expired or invalid"))
                        }
                        else -> handleErrorResponse(clientResponse.statusCode(), "Token refresh")
                    }
                }
                .bodyToMono(QuestradeTokenResponse::class.java)
                .awaitSingleOrNull()
                ?: throw BrokerApiException("Empty response from Questrade token refresh")

            successTotal.increment()
            recordLatency(startTime)

            log.info("Successfully refreshed token, new api_server: {}", response.apiServer)

            // IMPORTANT: Questrade returns a NEW refresh token - the old one is invalidated
            return BrokerTokenResponse(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                tokenType = response.tokenType,
                expiresIn = response.expiresIn?.toLong(),
                apiServerUrl = response.apiServer
            )
        } catch (e: BrokerException) {
            errorTotal.increment()
            throw e
        } catch (e: Exception) {
            errorTotal.increment()
            log.error("Token refresh failed: ${e.message}", e)
            throw BrokerTokenRefreshException("Token refresh failed: ${e.message}", e)
        }
    }

    override suspend fun revokeTokens(accessToken: String) {
        // Questrade doesn't have a token revocation endpoint
        // Tokens expire naturally or when refresh token is used
        log.info("Questrade token revocation not supported, token will expire naturally")
    }

    override suspend fun fetchAccounts(
        accessToken: String,
        apiServerUrl: String?
    ): List<BrokerAccountDto> {
        val baseUrl = requireNotNull(apiServerUrl) { "API server URL required for Questrade" }

        rateLimiter.acquire()
        requestsTotal.increment()

        val startTime = System.nanoTime()

        try {
            val response = questradeWebClient.get()
                .uri("${baseUrl}v1/accounts")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { clientResponse ->
                    when (clientResponse.statusCode()) {
                        HttpStatus.UNAUTHORIZED -> {
                            Mono.error(BrokerTokenExpiredException("Access token expired"))
                        }
                        HttpStatus.TOO_MANY_REQUESTS -> {
                            Mono.error(BrokerRateLimitException())
                        }
                        else -> handleErrorResponse(clientResponse.statusCode(), "Fetch accounts")
                    }
                }
                .bodyToMono(QuestradeAccountsResponse::class.java)
                .awaitSingleOrNull()
                ?: throw BrokerApiException("Empty response from Questrade accounts")

            successTotal.increment()
            recordLatency(startTime)

            log.info("Fetched {} accounts from Questrade", response.accounts.size)

            return response.accounts.map { account ->
                BrokerAccountDto(
                    accountId = account.number,
                    accountNumber = account.number,
                    accountType = account.type,
                    accountName = "${account.type} - ${account.number}",
                    currency = account.primaryCurrency ?: "CAD",
                    status = account.status,
                    metadata = mapOf(
                        "isBilling" to account.isBilling,
                        "isPrimary" to account.isPrimary
                    )
                )
            }
        } catch (e: BrokerException) {
            errorTotal.increment()
            throw e
        } catch (e: Exception) {
            errorTotal.increment()
            log.error("Fetch accounts failed: ${e.message}", e)
            throw BrokerApiException("Fetch accounts failed: ${e.message}", cause = e)
        }
    }

    override suspend fun fetchPositions(
        accessToken: String,
        accountId: String,
        apiServerUrl: String?
    ): BrokerPositionsResponse {
        val baseUrl = requireNotNull(apiServerUrl) { "API server URL required for Questrade" }

        rateLimiter.acquire()
        requestsTotal.increment()

        val startTime = System.nanoTime()

        try {
            val response = questradeWebClient.get()
                .uri("${baseUrl}v1/accounts/$accountId/positions")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .onStatus(HttpStatusCode::isError) { clientResponse ->
                    when (clientResponse.statusCode()) {
                        HttpStatus.UNAUTHORIZED -> {
                            Mono.error(BrokerTokenExpiredException("Access token expired"))
                        }
                        HttpStatus.TOO_MANY_REQUESTS -> {
                            Mono.error(BrokerRateLimitException())
                        }
                        else -> handleErrorResponse(clientResponse.statusCode(), "Fetch positions")
                    }
                }
                .bodyToMono(QuestradePositionsResponse::class.java)
                .awaitSingleOrNull()
                ?: throw BrokerApiException("Empty response from Questrade positions")

            successTotal.increment()
            recordLatency(startTime)

            log.info("Fetched {} positions for account {}", response.positions.size, accountId)

            val positions = response.positions.map { pos ->
                BrokerPositionDto(
                    symbolId = pos.symbolId?.toString(),
                    symbol = pos.symbol,
                    securityName = null, // Questrade doesn't return name in positions
                    instrumentType = mapInstrumentType(pos.assetClass),
                    quantity = pos.openQuantity,
                    averageCost = pos.averageEntryPrice,
                    currentPrice = pos.currentPrice,
                    currentValue = pos.currentMarketValue,
                    dayPnl = pos.dayPnl,
                    totalPnl = pos.openPnl,
                    totalPnlPercent = pos.totalCost?.let { cost ->
                        if (cost != BigDecimal.ZERO) {
                            pos.openPnl?.divide(cost, 4, java.math.RoundingMode.HALF_UP)
                                ?.multiply(BigDecimal(100))
                        } else null
                    },
                    currency = "CAD",
                    rawData = mapOf(
                        "symbolId" to pos.symbolId,
                        "closedQuantity" to pos.closedQuantity,
                        "closedPnl" to pos.closedPnl,
                        "totalCost" to pos.totalCost,
                        "isRealTime" to pos.isRealTime,
                        "isUnderReorg" to pos.isUnderReorg
                    )
                )
            }

            return BrokerPositionsResponse(
                positions = positions,
                rawPayload = objectMapper.writeValueAsString(response),
                asOfTimestamp = OffsetDateTime.now()
            )
        } catch (e: BrokerException) {
            errorTotal.increment()
            throw e
        } catch (e: Exception) {
            errorTotal.increment()
            log.error("Fetch positions failed: ${e.message}", e)
            throw BrokerApiException("Fetch positions failed: ${e.message}", cause = e)
        }
    }

    private fun mapInstrumentType(assetClass: String?): String? {
        return when (assetClass?.uppercase()) {
            "EQUITY" -> "STOCK"
            "ETF" -> "ETF"
            "MUTUAL_FUND", "MUTUALFUND" -> "MUTUAL_FUND"
            "OPTION" -> "OPTION"
            "BOND" -> "BOND"
            else -> assetClass
        }
    }

    private fun handleErrorResponse(status: HttpStatusCode, operation: String): Mono<Throwable> {
        return when (status) {
            HttpStatus.TOO_MANY_REQUESTS -> {
                Mono.error(BrokerRateLimitException())
            }
            HttpStatus.UNAUTHORIZED -> {
                Mono.error(BrokerTokenExpiredException())
            }
            HttpStatus.FORBIDDEN -> {
                Mono.error(BrokerApiException("$operation forbidden - check permissions", status.value()))
            }
            HttpStatus.NOT_FOUND -> {
                Mono.error(BrokerApiException("$operation resource not found", status.value()))
            }
            else -> {
                Mono.error(BrokerApiException("$operation failed with status ${status.value()}", status.value()))
            }
        }
    }

    private fun recordLatency(startTime: Long) {
        val duration = System.nanoTime() - startTime
        apiLatency.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS)
    }
}

// Questrade API response DTOs
data class QuestradeTokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val token_type: String = "Bearer",
    val expires_in: Int?,
    val api_server: String?
) {
    val accessToken: String get() = access_token
    val refreshToken: String? get() = refresh_token
    val tokenType: String get() = token_type
    val expiresIn: Int? get() = expires_in
    val apiServer: String? get() = api_server
}

data class QuestradeAccountsResponse(
    val accounts: List<QuestradeAccount>
)

data class QuestradeAccount(
    val type: String,
    val number: String,
    val status: String?,
    val isPrimary: Boolean?,
    val isBilling: Boolean?,
    val primaryCurrency: String?
)

data class QuestradePositionsResponse(
    val positions: List<QuestradePosition>
)

data class QuestradePosition(
    val symbol: String,
    val symbolId: Long?,
    val openQuantity: BigDecimal,
    val closedQuantity: BigDecimal?,
    val currentMarketValue: BigDecimal?,
    val currentPrice: BigDecimal?,
    val averageEntryPrice: BigDecimal?,
    val totalCost: BigDecimal?,
    val dayPnl: BigDecimal?,
    val openPnl: BigDecimal?,
    val closedPnl: BigDecimal?,
    val isRealTime: Boolean?,
    val isUnderReorg: Boolean?,
    val assetClass: String?
)
