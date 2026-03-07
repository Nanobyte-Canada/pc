package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.broker.service.BrokerService
import com.portfolio.broker.service.PositionFetchService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/brokers")
@PreAuthorize("isAuthenticated()")
class BrokerController(
    private val brokerService: BrokerService,
    private val positionFetchService: PositionFetchService,
    private val snapTradeConfig: SnapTradeConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Broker Listing ==========

    @GetMapping
    fun getAvailableBrokers(): ResponseEntity<BrokersResponse> {
        val brokers = brokerService.getAvailableBrokers()
        return ResponseEntity.ok(BrokersResponse(brokers))
    }

    @GetMapping("/config-status")
    fun getConfigStatus(): ResponseEntity<Map<String, Any>> {
        val status = mapOf(
            "clientIdConfigured" to snapTradeConfig.clientId.isNotBlank(),
            "consumerKeyConfigured" to snapTradeConfig.consumerKey.isNotBlank(),
            "redirectUri" to snapTradeConfig.redirectUri
        )
        return ResponseEntity.ok(status)
    }

    // ========== Diagnostic (temporary - remove after debugging) ==========

    @PostMapping("/debug-post")
    fun debugPost(request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val auth = SecurityContextHolder.getContext().authentication
        return ResponseEntity.ok(mapOf(
            "method" to request.method,
            "path" to request.servletPath,
            "authenticated" to (auth != null && auth.isAuthenticated),
            "principalType" to auth?.principal?.javaClass?.simpleName,
            "cookiesReceived" to (request.cookies?.map { it.name } ?: emptyList<String>()),
            "csrfHeader" to request.getHeader("X-XSRF-TOKEN"),
            "contentType" to request.contentType,
            "origin" to request.getHeader("Origin")
        ))
    }

    // ========== Connection Management ==========

    @GetMapping("/connections")
    fun getUserConnections(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BrokerConnectionsResponse> {
        val connections = brokerService.getUserConnections(principal.id)
        return ResponseEntity.ok(BrokerConnectionsResponse(connections))
    }

    @PostMapping("/connect")
    fun connectBroker(
        @RequestBody(required = false) request: ConnectBrokerRequest?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ConnectBrokerResponse> {
        val redirectUrl = brokerService.getConnectionPortalUrl(
            userId = principal.id,
            broker = request?.broker,
            reconnectAuthId = request?.reconnectAuthId
        )
        return ResponseEntity.ok(ConnectBrokerResponse(redirectUrl = redirectUrl))
    }

    @DeleteMapping("/connections/{authorizationId}")
    fun disconnectBroker(
        @PathVariable authorizationId: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        brokerService.disconnectBroker(authorizationId, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Position Fetching ==========

    @PostMapping("/connections/{connectionId}/fetch")
    fun triggerPositionFetch(
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PositionFetchResponse> {
        val fetchLog = positionFetchService.triggerManualFetch(connectionId, principal.id)
        return ResponseEntity.accepted().body(
            PositionFetchResponse(
                fetchId = fetchLog.id,
                status = fetchLog.status.name,
                message = "Position fetch initiated"
            )
        )
    }

    @GetMapping("/connections/{connectionId}/positions")
    fun getConnectionPositions(
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ConnectionPositionsResponse> {
        val response = brokerService.getPositionsForConnection(connectionId, principal.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/positions")
    fun getAggregatedPositions(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<AggregatedPositionsResponse> {
        val response = brokerService.getAggregatedPositions(principal.id)
        return ResponseEntity.ok(response)
    }

    // ========== Exception Handlers ==========

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = "BAD_REQUEST",
                message = e.message ?: "Invalid request"
            )
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        log.error("Broker operation error: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                error = "BROKER_ERROR",
                message = e.message ?: "Broker operation failed"
            )
        )
    }
}

data class ErrorResponse(
    val error: String,
    val message: String
)
