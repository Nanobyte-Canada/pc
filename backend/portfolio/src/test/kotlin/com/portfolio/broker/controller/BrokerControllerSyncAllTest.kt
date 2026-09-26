package com.portfolio.broker.controller

import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.client.GatewayApiException
import com.portfolio.broker.entity.PositionFetchLog
import com.portfolio.broker.service.ActivityIngestionService
import com.portfolio.broker.service.BrokerService
import com.portfolio.broker.service.DriftCalculationService
import com.portfolio.broker.service.PositionFetchService
import com.portfolio.broker.service.RebalanceService
import com.portfolio.broker.service.ReportingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.authority.SimpleGrantedAuthority

class BrokerControllerSyncAllTest {

    private lateinit var controller: BrokerController
    private lateinit var brokerService: BrokerService
    private lateinit var positionFetchService: PositionFetchService
    private lateinit var activityIngestionService: ActivityIngestionService

    private val principal = UserPrincipal(
        id = 1L,
        email = "user@example.com",
        passwordHash = null,
        authorities = listOf(SimpleGrantedAuthority("ROLE_USER")),
    )

    @BeforeEach
    fun setup() {
        brokerService = mockk()
        positionFetchService = mockk()
        activityIngestionService = mockk()
        controller = BrokerController(
            brokerService = brokerService,
            positionFetchService = positionFetchService,
            activityIngestionService = activityIngestionService,
            reportingService = mockk(relaxed = true),
            driftCalculationService = mockk(relaxed = true),
            rebalanceService = mockk(relaxed = true),
            gatewayClient = mockk<BrokerGatewayClient>(relaxed = true),
            userRepository = mockk<UserRepository>(relaxed = true),
        )
        every { brokerService.getConnection(10L, 1L) } returns mockk(relaxed = true)
    }

    private fun fetchLog(positions: Int?): PositionFetchLog {
        val log = mockk<PositionFetchLog>()
        every { log.positionsCount } returns positions
        return log
    }

    @Test
    fun `sync-all returns per-step statuses and keeps legacy keys on full success`() {
        every { positionFetchService.triggerManualFetch(10L, 1L) } returns fetchLog(7)
        every { activityIngestionService.syncActivitiesForConnection(10L) } returns 3
        every { activityIngestionService.syncBalanceForConnection(10L) } returns Unit

        val body = controller.syncAll(10L, principal).body!!

        assertEquals(10L, body["connectionId"])
        assertEquals(7, body["positionsFetched"])
        assertEquals(3, body["activitiesSynced"])
        assertEquals(true, body["balanceSynced"])
        assertEquals("SUCCESS", body["positionsStatus"])
        assertEquals("SUCCESS", body["activitiesStatus"])
        assertEquals("SUCCESS", body["balanceStatus"])
        assertEquals("SUCCESS", body["status"])
        assertEquals("Sync completed successfully", body["message"])
    }

    @Test
    fun `sync-all records FAILED positions and continues remaining steps when gateway throws`() {
        every {
            positionFetchService.triggerManualFetch(10L, 1L)
        } throws GatewayApiException(502, "BROKER_DATA_ERROR", "broker down")
        every { activityIngestionService.syncActivitiesForConnection(10L) } returns 2
        every { activityIngestionService.syncBalanceForConnection(10L) } returns Unit

        val body = controller.syncAll(10L, principal).body!!

        assertEquals(0, body["positionsFetched"])
        assertEquals("FAILED", body["positionsStatus"])
        assertEquals(2, body["activitiesSynced"])
        assertEquals("SUCCESS", body["activitiesStatus"])
        assertEquals(true, body["balanceSynced"])
        assertEquals("SUCCESS", body["balanceStatus"])
        assertEquals("PARTIAL", body["status"])
        assertTrue(body["message"]!!.toString().contains("positions"))
        // the later steps still ran — the controller did not abort on the first failure
        verify { activityIngestionService.syncActivitiesForConnection(10L) }
        verify { activityIngestionService.syncBalanceForConnection(10L) }
    }

    @Test
    fun `sync-all reports FAILED balance and PARTIAL overall when balance throws`() {
        every { positionFetchService.triggerManualFetch(10L, 1L) } returns fetchLog(null)
        every { activityIngestionService.syncActivitiesForConnection(10L) } returns 0
        every {
            activityIngestionService.syncBalanceForConnection(10L)
        } throws GatewayApiException(502, "GATEWAY_TIMEOUT", "gateway timeout")

        val body = controller.syncAll(10L, principal).body!!

        assertEquals(0, body["positionsFetched"])
        assertEquals("SUCCESS", body["positionsStatus"])
        assertEquals(false, body["balanceSynced"])
        assertEquals("FAILED", body["balanceStatus"])
        assertEquals("PARTIAL", body["status"])
        assertTrue(body["message"]!!.toString().contains("balance"))
    }

    @Test
    fun `sync-activities propagates gateway failure instead of masking it`() {
        val ex = GatewayApiException(502, "BROKER_DATA_ERROR", "broker down")
        every { activityIngestionService.syncActivitiesForConnection(10L) } throws ex

        val thrown = assertThrows<GatewayApiException> {
            controller.syncActivities(10L, principal)
        }
        assertEquals("BROKER_DATA_ERROR", thrown.gatewayErrorCode)
    }
}
