package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.entity.BrokerActivity
import com.portfolio.broker.entity.BrokerBalanceSnapshot
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportingServiceTest {

    private lateinit var service: ReportingService
    private lateinit var connectionRepository: BrokerConnectionRepository
    private lateinit var activityRepository: BrokerActivityRepository
    private lateinit var balanceRepository: BrokerBalanceRepository
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        connectionRepository = mockk()
        activityRepository = mockk()
        balanceRepository = mockk()

        service = ReportingService(connectionRepository, activityRepository, balanceRepository, objectMapper)
    }

    private fun mockConnection(id: Long, status: ConnectionStatus = ConnectionStatus.ACTIVE): BrokerConnection {
        return mockk {
            every { this@mockk.id } returns id
            every { this@mockk.status } returns status
        }
    }

    private fun mockActivity(
        type: String,
        amount: BigDecimal,
        tradeDate: LocalDate,
        symbol: String? = null,
        connectionId: Long = 1L
    ): BrokerActivity {
        val conn = mockk<BrokerConnection> { every { id } returns connectionId }
        return mockk {
            every { this@mockk.type } returns type
            every { this@mockk.amount } returns amount
            every { this@mockk.tradeDate } returns tradeDate
            every { this@mockk.symbol } returns symbol
            every { this@mockk.connection } returns conn
        }
    }

    @Test
    fun `contributions and withdrawals aggregated correctly by month`() {
        every { connectionRepository.findByUserId(1L) } returns listOf(mockConnection(10L))
        every { activityRepository.findByConnectionIdInAndTradeDateBetween(any(), any(), any()) } returns listOf(
            mockActivity("TRANSFER_IN", BigDecimal("5000"), LocalDate.of(2024, 1, 15)),
            mockActivity("TRANSFER_IN", BigDecimal("3000"), LocalDate.of(2024, 1, 20)),
            mockActivity("TRANSFER_OUT", BigDecimal("-2000"), LocalDate.of(2024, 1, 25)),
            mockActivity("TRANSFER_IN", BigDecimal("1000"), LocalDate.of(2024, 2, 10))
        )
        every { balanceRepository.findByConnectionIdInAndAsOfDateBetween(any(), any(), any()) } returns emptyList()

        val result = service.getPerformanceReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null)

        assertEquals(2, result.contributionsWithdrawals.size)
        val jan = result.contributionsWithdrawals.find { it.period == "2024-01" }!!
        assertEquals(BigDecimal("8000"), jan.contributions)
        assertEquals(BigDecimal("2000"), jan.withdrawals)
        assertEquals(BigDecimal("6000"), jan.net)

        val feb = result.contributionsWithdrawals.find { it.period == "2024-02" }!!
        assertEquals(BigDecimal("1000"), feb.contributions)
        assertEquals(BigDecimal.ZERO, feb.withdrawals)
    }

    @Test
    fun `dividend history groups by symbol and period`() {
        every { connectionRepository.findByUserId(1L) } returns listOf(mockConnection(10L))
        every { activityRepository.findByConnectionIdInAndTradeDateBetween(any(), any(), any()) } returns listOf(
            mockActivity("DIVIDEND", BigDecimal("50"), LocalDate.of(2024, 3, 15), "VFV.TO"),
            mockActivity("DIVIDEND", BigDecimal("30"), LocalDate.of(2024, 3, 20), "XIC.TO"),
            mockActivity("DIVIDEND", BigDecimal("60"), LocalDate.of(2024, 6, 15), "VFV.TO")
        )
        every { balanceRepository.findByConnectionIdInAndAsOfDateBetween(any(), any(), any()) } returns emptyList()

        val result = service.getPerformanceReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null)

        assertEquals(2, result.dividendHistory.size)
        val march = result.dividendHistory.find { it.period == "2024-03" }!!
        assertEquals(BigDecimal("80"), march.total)
        assertEquals(BigDecimal("50"), march.bySymbol["VFV.TO"])
        assertEquals(BigDecimal("30"), march.bySymbol["XIC.TO"])

        assertEquals(2, result.totalDividendsBySymbol.size)
        assertEquals("VFV.TO", result.totalDividendsBySymbol[0].symbol) // highest first
        assertEquals(BigDecimal("110"), result.totalDividendsBySymbol[0].total)
    }

    @Test
    fun `KPI calculations are correct`() {
        every { connectionRepository.findByUserId(1L) } returns listOf(mockConnection(10L))
        every { activityRepository.findByConnectionIdInAndTradeDateBetween(any(), any(), any()) } returns listOf(
            mockActivity("TRANSFER_IN", BigDecimal("10000"), LocalDate.of(2024, 1, 15)),
            mockActivity("TRANSFER_OUT", BigDecimal("-3000"), LocalDate.of(2024, 2, 15)),
            mockActivity("DIVIDEND", BigDecimal("100"), LocalDate.of(2024, 1, 20), "VFV.TO"),
            mockActivity("DIVIDEND", BigDecimal("200"), LocalDate.of(2024, 2, 20), "VFV.TO"),
            mockActivity("FEE", BigDecimal("-9.99"), LocalDate.of(2024, 1, 30))
        )
        every { balanceRepository.findByConnectionIdInAndAsOfDateBetween(any(), any(), any()) } returns emptyList()

        val result = service.getPerformanceReport(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null)

        val kpis = result.kpis
        assertEquals(BigDecimal("7000"), kpis.netContributions) // 10000 - 3000
        assertEquals(BigDecimal("300"), kpis.totalDividendIncome)
        assertEquals(BigDecimal("9.99"), kpis.feesAndCommissions)
    }

    @Test
    fun `empty data returns zero KPIs and empty arrays`() {
        every { connectionRepository.findByUserId(1L) } returns listOf(mockConnection(10L))
        every { activityRepository.findByConnectionIdInAndTradeDateBetween(any(), any(), any()) } returns emptyList()
        every { balanceRepository.findByConnectionIdInAndAsOfDateBetween(any(), any(), any()) } returns emptyList()

        val result = service.getPerformanceReport(1L, null, null, null)

        assertTrue(result.contributionsWithdrawals.isEmpty())
        assertTrue(result.totalValueHistory.isEmpty())
        assertTrue(result.dividendHistory.isEmpty())
        assertTrue(result.totalDividendsBySymbol.isEmpty())
        assertEquals(BigDecimal.ZERO, result.kpis.netContributions)
        assertEquals(BigDecimal.ZERO, result.kpis.totalDividendIncome)
        assertEquals(BigDecimal.ZERO, result.kpis.feesAndCommissions)
    }

    @Test
    fun `no connections returns empty performance response`() {
        every { connectionRepository.findByUserId(99L) } returns emptyList()

        val result = service.getPerformanceReport(99L, null, null, null)

        assertTrue(result.contributionsWithdrawals.isEmpty())
        assertEquals(BigDecimal.ZERO, result.kpis.netContributions)
    }
}
