package com.portfolio.auth.service

import com.portfolio.auth.config.AuthConfig
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*

@Service
class PasswordService(
    private val authConfig: AuthConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        // Argon2id parameters (OWASP recommended)
        private const val SALT_LENGTH = 16
        private const val HASH_LENGTH = 32
        private const val PARALLELISM = 4
        private const val MEMORY_KB = 65536  // 64 MB
        private const val ITERATIONS = 3
    }

    /**
     * Hash a password using Argon2id
     */
    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val hash = generateArgon2Hash(password, salt)

        // Encode as: $argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>
        val saltBase64 = Base64.getEncoder().withoutPadding().encodeToString(salt)
        val hashBase64 = Base64.getEncoder().withoutPadding().encodeToString(hash)

        return "\$argon2id\$v=19\$m=$MEMORY_KB,t=$ITERATIONS,p=$PARALLELISM\$$saltBase64\$$hashBase64"
    }

    /**
     * Verify a password against a stored hash
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            val parts = parseArgon2Hash(storedHash)
            if (parts == null) {
                logger.warn("Invalid hash format")
                return false
            }

            val (salt, expectedHash, memory, iterations, parallelism) = parts
            val computedHash = generateArgon2Hash(password, salt, memory, iterations, parallelism)

            // Constant-time comparison
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (e: Exception) {
            logger.error("Error verifying password", e)
            false
        }
    }

    /**
     * Validate password strength
     */
    fun validatePasswordStrength(password: String): PasswordValidationResult {
        val errors = mutableListOf<String>()

        if (password.length < authConfig.password.minLength) {
            errors.add("Password must be at least ${authConfig.password.minLength} characters")
        }

        if (!password.any { it.isUpperCase() }) {
            errors.add("Password must contain at least one uppercase letter")
        }

        if (!password.any { it.isLowerCase() }) {
            errors.add("Password must contain at least one lowercase letter")
        }

        if (!password.any { it.isDigit() }) {
            errors.add("Password must contain at least one number")
        }

        if (!password.any { !it.isLetterOrDigit() }) {
            errors.add("Password must contain at least one special character")
        }

        // Check for common patterns
        val commonPatterns = listOf("password", "123456", "qwerty", "admin", "letmein")
        if (commonPatterns.any { password.lowercase().contains(it) }) {
            errors.add("Password contains a common pattern")
        }

        return PasswordValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    private fun generateArgon2Hash(
        password: String,
        salt: ByteArray,
        memory: Int = MEMORY_KB,
        iterations: Int = ITERATIONS,
        parallelism: Int = PARALLELISM
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memory)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val hash = ByteArray(HASH_LENGTH)
        generator.generateBytes(password.toCharArray(), hash)

        return hash
    }

    private fun parseArgon2Hash(hash: String): Argon2HashParts? {
        // Format: $argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>
        val parts = hash.split("$").filter { it.isNotEmpty() }
        if (parts.size != 5 || parts[0] != "argon2id") {
            return null
        }

        val params = parts[2].split(",").associate {
            val (key, value) = it.split("=")
            key to value.toInt()
        }

        val salt = Base64.getDecoder().decode(parts[3])
        val hashBytes = Base64.getDecoder().decode(parts[4])

        return Argon2HashParts(
            salt = salt,
            hash = hashBytes,
            memory = params["m"] ?: MEMORY_KB,
            iterations = params["t"] ?: ITERATIONS,
            parallelism = params["p"] ?: PARALLELISM
        )
    }
}

private object MessageDigest {
    fun isEqual(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}

data class Argon2HashParts(
    val salt: ByteArray,
    val hash: ByteArray,
    val memory: Int,
    val iterations: Int,
    val parallelism: Int
)

data class PasswordValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)
