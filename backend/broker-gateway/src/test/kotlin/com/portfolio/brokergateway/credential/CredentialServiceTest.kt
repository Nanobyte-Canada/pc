package com.portfolio.brokergateway.credential

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.exception.ConnectionNotFoundException
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals

class CredentialServiceTest {

    private val repository = mockk<GatewayConnectionRepository>()
    private val encryptionService = TokenEncryptionService("")
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val service = CredentialService(repository, encryptionService, objectMapper)

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `createConnection stores encrypted credentials and returns id`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 9999999999
        )

        every { repository.save(any()) } answers { firstArg() }

        val id = service.createConnection(userId = 1L, credentials = creds)

        verify { repository.save(match { it.userId == 1L && it.brokerType == "QUESTRADE" }) }
        assert(id.isNotBlank())
    }

    @Test
    fun `getCredentials decrypts and returns correct subtype`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val json = objectMapper.writeValueAsString(creds)
        val encrypted = encryptionService.encrypt(json)
        val entity = GatewayConnection(
            id = "conn-1", userId = 1L, brokerType = "IBKR", credentialsEncrypted = encrypted
        )

        every { repository.findById("conn-1") } returns Optional.of(entity)

        val result = service.getCredentials("conn-1")
        assert(result is BrokerCredentials.IbkrCredentials)
        assertEquals("127.0.0.1", (result as BrokerCredentials.IbkrCredentials).host)
    }

    @Test
    fun `getCredentials throws ConnectionNotFoundException for unknown id`() {
        every { repository.findById("unknown") } returns Optional.empty()
        assertThrows<ConnectionNotFoundException> { service.getCredentials("unknown") }
    }

    @Test
    fun `getConnection throws ConnectionNotFoundException for unknown id`() {
        every { repository.findById("unknown") } returns Optional.empty()
        assertThrows<ConnectionNotFoundException> { service.getConnection("unknown") }
    }

    @Test
    fun `listConnections returns connections for user`() {
        val entity = GatewayConnection(
            id = "c1", userId = 5L, brokerType = "QUESTRADE", credentialsEncrypted = "x"
        )
        every { repository.findByUserId(5L) } returns listOf(entity)

        val result = service.listConnections(5L)
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
    }

    @Test
    fun `deleteConnection removes from repository`() {
        val entity = GatewayConnection(
            id = "c1", userId = 5L, brokerType = "QUESTRADE", credentialsEncrypted = "x"
        )
        every { repository.findById("c1") } returns Optional.of(entity)
        every { repository.delete(entity) } just Runs

        service.deleteConnection("c1")
        verify { repository.delete(entity) }
    }

    @Test
    fun `updateCredentials re-encrypts and persists`() {
        val oldCreds = BrokerCredentials.QuestradeCredentials(
            accessToken = "old", refreshToken = "old-refresh",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 1000
        )
        val json = objectMapper.writeValueAsString(oldCreds)
        val encrypted = encryptionService.encrypt(json)
        val entity = GatewayConnection(
            id = "c1", userId = 1L, brokerType = "QUESTRADE", credentialsEncrypted = encrypted
        )

        every { repository.findById("c1") } returns Optional.of(entity)
        every { repository.save(any()) } answers { firstArg() }

        val newCreds = BrokerCredentials.QuestradeCredentials(
            accessToken = "new", refreshToken = "new-refresh",
            apiServerUrl = "https://api06.iq.questrade.com/", expiresAtEpochSeconds = 2000
        )

        service.updateCredentials("c1", newCreds)

        verify {
            repository.save(match {
                it.id == "c1" && it.credentialsEncrypted != encrypted
            })
        }
    }
}
