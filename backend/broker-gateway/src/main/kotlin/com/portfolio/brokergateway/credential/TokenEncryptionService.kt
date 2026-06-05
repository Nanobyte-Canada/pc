package com.portfolio.brokergateway.credential

import com.portfolio.brokergateway.config.GatewayProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class TokenEncryptionService(
    @Value("\${broker-gateway.encryption.secret-key:}")
    private val secretKeyBase64: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val KEY_LENGTH = 32
    }

    private val secretKey: SecretKey by lazy {
        if (secretKeyBase64.isBlank()) {
            log.warn("No encryption key configured, generating ephemeral key")
            generateKey()
        } else {
            val keyBytes = Base64.getDecoder().decode(secretKeyBase64)
            require(keyBytes.size == KEY_LENGTH) { "Encryption key must be $KEY_LENGTH bytes" }
            SecretKeySpec(keyBytes, KEY_ALGORITHM)
        }
    }

    fun encrypt(plaintext: String): String {
        require(plaintext.isNotBlank()) { "Cannot encrypt empty value" }

        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()

        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedToken: String): String {
        require(encryptedToken.isNotBlank()) { "Cannot decrypt empty value" }

        val combined = Base64.getDecoder().decode(encryptedToken)
        require(combined.size > GCM_IV_LENGTH) { "Invalid encrypted value: too short" }

        val buffer = ByteBuffer.wrap(combined)
        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun validateConfiguration(): Boolean {
        return try {
            val test = "validation-test"
            decrypt(encrypt(test)) == test
        } catch (e: Exception) {
            log.error("Encryption validation failed: ${e.message}")
            false
        }
    }

    private fun generateKey(): SecretKey {
        val keyBytes = ByteArray(KEY_LENGTH)
        secureRandom.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }
}
