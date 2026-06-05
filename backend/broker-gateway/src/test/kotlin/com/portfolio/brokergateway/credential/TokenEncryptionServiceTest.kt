package com.portfolio.brokergateway.credential

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenEncryptionServiceTest {

    private val service = TokenEncryptionService("")

    @Test
    fun `encrypt and decrypt round-trips correctly`() {
        val plaintext = """{"brokerType":"QUESTRADE","accessToken":"abc123"}"""
        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypted output differs from plaintext`() {
        val plaintext = "secret-token"
        val encrypted = service.encrypt(plaintext)
        assertNotEquals(plaintext, encrypted)
    }

    @Test
    fun `same plaintext produces different ciphertext each time`() {
        val plaintext = "secret-token"
        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)
        assertNotEquals(encrypted1, encrypted2)
    }

    @Test
    fun `encrypt rejects blank input`() {
        assertThrows<IllegalArgumentException> { service.encrypt("") }
        assertThrows<IllegalArgumentException> { service.encrypt("   ") }
    }

    @Test
    fun `decrypt rejects blank input`() {
        assertThrows<IllegalArgumentException> { service.decrypt("") }
    }

    @Test
    fun `validateConfiguration returns true`() {
        assertTrue(service.validateConfiguration())
    }
}
