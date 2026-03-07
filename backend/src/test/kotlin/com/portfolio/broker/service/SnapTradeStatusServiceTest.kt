package com.portfolio.broker.service

import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.broker.entity.SnapTradeApiStatus
import com.portfolio.broker.entity.SnapTradeStatusCheck
import com.portfolio.broker.repository.SnapTradeStatusRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SnapTradeStatusServiceTest {

    private lateinit var service: SnapTradeStatusService
    private lateinit var statusRepository: SnapTradeStatusRepository
    private lateinit var config: SnapTradeConfig

    @BeforeEach
    fun setup() {
        statusRepository = mockk()
        config = SnapTradeConfig(clientId = "test-id", consumerKey = "test-key")
        service = SnapTradeStatusService(statusRepository, config)
    }

    // ========== getLatestStatus Tests ==========

    @Test
    fun `getLatestStatus returns null when no checks exist`() {
        every { statusRepository.findLatest() } returns null

        val result = service.getLatestStatus()

        assertNull(result)
    }

    @Test
    fun `getLatestStatus returns DTO with uptime when checks exist`() {
        val check = SnapTradeStatusCheck(
            id = 1L,
            status = SnapTradeApiStatus.ONLINE,
            responseTimeMs = 150,
            version = "2.0.0",
            checkedAt = OffsetDateTime.now()
        )
        every { statusRepository.findLatest() } returns check
        every { statusRepository.countChecksSince(any()) } returns 10L
        every { statusRepository.countByStatusSince(SnapTradeApiStatus.ONLINE, any()) } returns 9L

        val result = service.getLatestStatus()

        assertNotNull(result)
        assertEquals("ONLINE", result.status)
        assertEquals(150, result.responseTimeMs)
        assertEquals("2.0.0", result.version)
        assertEquals(90.0, result.uptimePercent24h)
    }

    @Test
    fun `getLatestStatus returns OFFLINE status with error message`() {
        val check = SnapTradeStatusCheck(
            id = 2L,
            status = SnapTradeApiStatus.OFFLINE,
            responseTimeMs = 0,
            errorMessage = "Connection refused",
            checkedAt = OffsetDateTime.now()
        )
        every { statusRepository.findLatest() } returns check
        every { statusRepository.countChecksSince(any()) } returns 5L
        every { statusRepository.countByStatusSince(SnapTradeApiStatus.ONLINE, any()) } returns 2L

        val result = service.getLatestStatus()

        assertNotNull(result)
        assertEquals("OFFLINE", result.status)
        assertEquals(40.0, result.uptimePercent24h)
    }

    // ========== getUptimePercentage Tests ==========

    @Test
    fun `getUptimePercentage returns 100 when no checks exist`() {
        every { statusRepository.countChecksSince(any()) } returns 0L

        val result = service.getUptimePercentage()

        assertEquals(100.0, result)
    }

    @Test
    fun `getUptimePercentage calculates correctly with mixed statuses`() {
        every { statusRepository.countChecksSince(any()) } returns 20L
        every { statusRepository.countByStatusSince(SnapTradeApiStatus.ONLINE, any()) } returns 15L

        val result = service.getUptimePercentage()

        assertEquals(75.0, result)
    }

    @Test
    fun `getUptimePercentage returns 0 when all checks are offline`() {
        every { statusRepository.countChecksSince(any()) } returns 10L
        every { statusRepository.countByStatusSince(SnapTradeApiStatus.ONLINE, any()) } returns 0L

        val result = service.getUptimePercentage()

        assertEquals(0.0, result)
    }
}
