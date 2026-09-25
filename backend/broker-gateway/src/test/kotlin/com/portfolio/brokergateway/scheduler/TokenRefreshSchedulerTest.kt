package com.portfolio.brokergateway.scheduler

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.credential.GatewayConnection
import com.portfolio.brokergateway.credential.GatewayConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class TokenRefreshSchedulerTest {

    private val connectionRepository = mockk<GatewayConnectionRepository>(relaxed = true)
    private val credentialService = mockk<CredentialService>(relaxed = true)
    private val adapterRegistry = mockk<AdapterRegistry>(relaxed = true)
    private val adapter = mockk<BrokerAdapter>(relaxed = true)
    private val scheduler = TokenRefreshScheduler(connectionRepository, credentialService, adapterRegistry)

    private fun credentials(): BrokerCredentials = mockk(relaxed = true) {
        every { brokerType } returns BrokerType.QUESTRADE
    }

    private fun activeConnection(
        id: String = "uuid-1",
        lastValidatedAt: OffsetDateTime? = OffsetDateTime.now().minusHours(1),
    ): GatewayConnection {
        var failureCount = 0   // stateful: the scheduler increments this on failure
        return mockk<GatewayConnection>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.status } returns "ACTIVE"
            every { this@mockk.lastValidatedAt } returns lastValidatedAt
            every { this@mockk.refreshFailureCount } answers { failureCount }
            every { this@mockk.refreshFailureCount = any() } answers { failureCount = firstArg() }
        }
    }

    private fun wireCommon(conn: GatewayConnection) {
        every { connectionRepository.findByStatusIn(listOf("ACTIVE", "ERROR")) } returns listOf(conn)
        every { connectionRepository.save(any()) } answers { firstArg() }
        every { credentialService.getCredentials(conn.id) } returns credentials()
        every { adapterRegistry.getAdapter(any()) } returns adapter
        every { credentialService.getCredentialsWithRefresh(conn.id, adapter) } returns credentials()
    }

    @Test
    fun `fresh token with recent validation skips broker validation`() {
        val conn = activeConnection()
        wireCommon(conn)

        scheduler.refreshExpiringTokens()

        verify(exactly = 0) { adapter.validateConnection(any()) }
    }

    @Test
    fun `stale validation performs one broker call and stamps lastValidatedAt`() {
        val conn = activeConnection(lastValidatedAt = OffsetDateTime.now().minusHours(25))
        wireCommon(conn)
        every { adapter.validateConnection(any()) } returns mockk(relaxed = true) { every { connected } returns true }

        scheduler.refreshExpiringTokens()

        verify(exactly = 1) { adapter.validateConnection(any()) }
        verify { conn.lastValidatedAt = any() }
    }

    @Test
    fun `validation failure backs off so the next run skips the connection`() {
        val conn = activeConnection(lastValidatedAt = null)   // forces validation
        wireCommon(conn)
        every { adapter.validateConnection(any()) } throws RuntimeException("broker down")

        scheduler.refreshExpiringTokens()
        scheduler.refreshExpiringTokens()

        verify(exactly = 1) { adapter.validateConnection(any()) }   // second run was inside the backoff window
    }
}
