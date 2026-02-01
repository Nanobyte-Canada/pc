package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.client.BrokerException
import com.portfolio.broker.client.OAuthStateException
import com.portfolio.broker.dto.*
import com.portfolio.broker.service.BrokerService
import com.portfolio.broker.service.PositionFetchService
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/brokers")
@PreAuthorize("isAuthenticated()")
class BrokerController(
    private val brokerService: BrokerService,
    private val positionFetchService: PositionFetchService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Broker Listing ==========

    @GetMapping
    fun getAvailableBrokers(): ResponseEntity<BrokersResponse> {
        val brokers = brokerService.getAvailableBrokers()
        return ResponseEntity.ok(BrokersResponse(brokers))
    }

    // ========== Connection Management ==========

    @GetMapping("/connections")
    fun getUserConnections(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BrokerConnectionsResponse> {
        val connections = brokerService.getUserConnections(principal.id)
        return ResponseEntity.ok(BrokerConnectionsResponse(connections))
    }

    @PostMapping("/{code}/connect")
    fun initiateConnection(
        @PathVariable code: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<OAuthInitiateResponse> {
        val response = brokerService.initiateOAuthFlow(code, principal.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{code}/callback")
    @PreAuthorize("permitAll()")  // OAuth callback is public - validated via state token
    fun handleOAuthCallback(
        @PathVariable code: String,
        @RequestParam("code") authCode: String,
        @RequestParam("state") state: String,
        @RequestParam("error", required = false) error: String?,
        @RequestParam("error_description", required = false) errorDescription: String?,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        // Handle OAuth provider errors (user denied access, etc.)
        if (error != null) {
            log.warn("OAuth error from provider: $error - $errorDescription")
            response.sendRedirect("/brokers/connections?error=${error}&message=${errorDescription ?: ""}")
            return ResponseEntity.badRequest().build()
        }

        return try {
            val connection = brokerService.handleOAuthCallback(code, authCode, state)
            // Redirect to frontend with success
            response.sendRedirect("/brokers/connections?success=true&broker=${code}")
            ResponseEntity.ok().build()
        } catch (e: OAuthStateException) {
            log.error("OAuth state error: ${e.message}")
            response.sendRedirect("/brokers/connections?error=state_invalid&message=${e.message ?: ""}")
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error("OAuth callback error: ${e.message}", e)
            response.sendRedirect("/brokers/connections?error=connection_failed&message=${e.message ?: ""}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/connections/{id}")
    fun disconnectBroker(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        brokerService.disconnectBroker(id, principal.id)
        return ResponseEntity.noContent().build()
    }

    // ========== Position Fetching ==========

    @PostMapping("/connections/{id}/fetch")
    fun triggerPositionFetch(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PositionFetchResponse> {
        val fetchLog = positionFetchService.triggerManualFetch(id, principal.id)
        return ResponseEntity.accepted().body(
            PositionFetchResponse(
                fetchId = fetchLog.id,
                status = fetchLog.status.name,
                message = "Position fetch initiated"
            )
        )
    }

    @GetMapping("/connections/{id}/positions")
    fun getConnectionPositions(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ConnectionPositionsResponse> {
        val response = brokerService.getPositionsForConnection(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/positions")
    fun getAggregatedPositions(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<AggregatedPositionsResponse> {
        val response = brokerService.getAggregatedPositions(principal.id)
        return ResponseEntity.ok(response)
    }

    // ========== User Preferences ==========

    @GetMapping("/preferences")
    fun getUserPreferences(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<BrokerPrefsDto> {
        val prefs = brokerService.getUserPrefs(principal.id)
        return ResponseEntity.ok(prefs)
    }

    @PutMapping("/preferences")
    fun updateUserPreferences(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: UpdateBrokerPrefsRequest
    ): ResponseEntity<BrokerPrefsResponse> {
        val response = brokerService.updateUserPrefs(principal.id, request)
        return ResponseEntity.ok(response)
    }

    // ========== Exception Handlers ==========

    @ExceptionHandler(BrokerException::class)
    fun handleBrokerException(e: BrokerException): ResponseEntity<ErrorResponse> {
        log.error("Broker error: ${e.message}", e)
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = e.errorCode,
                message = e.message ?: "Broker operation failed"
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = "BAD_REQUEST",
                message = e.message ?: "Invalid request"
            )
        )
    }
}

data class ErrorResponse(
    val error: String,
    val message: String
)
