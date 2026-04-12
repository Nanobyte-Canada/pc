package com.portfolio.broker.controller

import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.broker.service.ActivityIngestionService
import com.portfolio.broker.service.BrokerService
import com.portfolio.broker.service.DriftCalculationService
import com.portfolio.broker.service.PositionFetchService
import com.portfolio.broker.service.RebalanceService
import com.portfolio.broker.service.ReportingService
import com.portfolio.broker.service.SnapTradeStatusService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/brokers")
@PreAuthorize("isAuthenticated()")
class BrokerController(
    private val brokerService: BrokerService,
    private val positionFetchService: PositionFetchService,
    private val snapTradeConfig: SnapTradeConfig,
    private val snapTradeStatusService: SnapTradeStatusService,
    private val activityIngestionService: ActivityIngestionService,
    private val reportingService: ReportingService,
    private val driftCalculationService: DriftCalculationService,
    private val rebalanceService: RebalanceService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Broker Listing ==========

    @GetMapping
    fun getAvailableBrokers(): ResponseEntity<BrokersResponse> {
        val brokers = brokerService.getAvailableBrokers()
        return ResponseEntity.ok(BrokersResponse(brokers))
    }

    @GetMapping("/authorization-types")
    fun getBrokerageAuthorizationTypes(
        @RequestParam(required = false) brokerage: String?
    ): ResponseEntity<List<BrokerAuthTypeDto>> {
        val authTypes = brokerService.getBrokerageAuthorizationTypes(brokerage)
        return ResponseEntity.ok(authTypes)
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

    // ========== SnapTrade Status ==========

    @GetMapping("/snaptrade/status")
    fun getSnapTradeStatus(): ResponseEntity<SnapTradeStatusResponse> {
        val status = snapTradeStatusService.getLatestStatus()
            ?: return ResponseEntity.ok(
                SnapTradeStatusResponse(
                    SnapTradeStatusDto(
                        status = "UNKNOWN",
                        responseTimeMs = null,
                        version = null,
                        uptimePercent24h = 100.0,
                        lastChecked = java.time.OffsetDateTime.now()
                    )
                )
            )
        return ResponseEntity.ok(SnapTradeStatusResponse(status))
    }

    // ========== Connection Sync ==========

    @PostMapping("/connections/sync")
    fun syncConnections(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ConnectionSyncResponse> {
        brokerService.syncConnections(principal.id)
        val connections = brokerService.getUserConnections(principal.id)

        // Auto-fetch data for newly synced connections that have no data yet
        val rawConnections = brokerService.getUserConnectionEntities(principal.id)
        for (conn in rawConnections) {
            if (conn.lastPositionsFetchedAt == null && conn.accountIdExternal != null) {
                try {
                    positionFetchService.triggerManualFetch(conn.id, principal.id)
                    activityIngestionService.syncActivitiesForConnection(conn.id)
                    activityIngestionService.syncBalanceForConnection(conn.id)
                } catch (e: Exception) {
                    log.warn("Auto-fetch failed for connection {}: {}", conn.id, e.message)
                }
            }
        }

        return ResponseEntity.ok(
            ConnectionSyncResponse(
                syncedCount = connections.size,
                message = "Connections synchronized successfully"
            )
        )
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
            reconnectAuthId = request?.reconnectAuthId,
            connectionType = request?.connectionType
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

    // ========== Activities ==========

    @GetMapping("/connections/{connectionId}/activities")
    fun getActivities(
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<ActivitiesResponse> {
        brokerService.getConnection(connectionId, principal.id) // auth check
        val result = reportingService.getActivitiesReport(
            userId = principal.id,
            page = page,
            size = size,
            startDate = startDate?.let { java.time.LocalDate.parse(it) },
            endDate = endDate?.let { java.time.LocalDate.parse(it) },
            connectionIds = listOf(connectionId),
            type = type
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/connections/{connectionId}/sync-activities")
    fun syncActivities(
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Map<String, Any>> {
        brokerService.getConnection(connectionId, principal.id) // auth check
        val count = activityIngestionService.syncActivitiesForConnection(connectionId)
        return ResponseEntity.ok(mapOf(
            "activitiesSynced" to count,
            "message" to "Activities synced successfully"
        ))
    }

    // ========== Balances ==========

    @GetMapping("/connections/{connectionId}/balance-history")
    fun getBalanceHistory(
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "90") days: Int
    ): ResponseEntity<BalanceHistoryResponse> {
        brokerService.getConnection(connectionId, principal.id) // auth check
        val endDate = java.time.LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())
        val response = brokerService.getBalanceHistory(connectionId, startDate, endDate)
        return ResponseEntity.ok(response)
    }

    // ========== Reporting (cross-account) ==========

    @GetMapping("/reporting/performance")
    fun getPerformanceReport(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) accounts: String?,
        @RequestParam(required = false) granularity: String?
    ): ResponseEntity<ReportingPerformanceResponse> {
        val startLocalDate = startDate?.let { java.time.LocalDate.parse(it) }
        val endLocalDate = endDate?.let { java.time.LocalDate.parse(it) }
        val connectionIds = accounts?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
        val response = reportingService.getPerformanceReport(
            userId = principal.id,
            startDate = startLocalDate,
            endDate = endLocalDate,
            connectionIds = connectionIds,
            granularity = granularity
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/reporting/activities")
    fun getReportingActivities(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) accounts: String?,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<ActivitiesResponse> {
        val startLocalDate = startDate?.let { java.time.LocalDate.parse(it) }
        val endLocalDate = endDate?.let { java.time.LocalDate.parse(it) }
        val connectionIds = accounts?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
        val response = reportingService.getActivitiesReport(
            userId = principal.id,
            page = page,
            size = size,
            startDate = startLocalDate,
            endDate = endLocalDate,
            connectionIds = connectionIds,
            type = type
        )
        return ResponseEntity.ok(response)
    }

    // ========== Model Rebalance ==========

    @GetMapping("/connections/{connectionId}/rebalance-progress")
    fun getRebalanceProgress(
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RebalanceProgressDto> {
        val connection = brokerService.getConnection(connectionId, principal.id)
        val progress = driftCalculationService.getRebalanceProgress(connection)
        return ResponseEntity.ok(progress)
    }

    @GetMapping("/connections/{connectionId}/pending-orders")
    fun getPendingOrders(
        @PathVariable connectionId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PendingOrdersResponse> {
        val connection = brokerService.getConnection(connectionId, principal.id)
        val orders = rebalanceService.calculateTradesForAccount(connection)
        return ResponseEntity.ok(orders)
    }

}
