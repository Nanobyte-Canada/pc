package com.portfolio.auth.security

import com.portfolio.auth.config.AuthConfig
import com.portfolio.auth.entity.User
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val authConfig: AuthConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val secretKey: SecretKey by lazy {
        val keyString = authConfig.jwt.signingKey
        if (keyString.length < 64) {
            throw IllegalStateException("JWT signing key must be at least 64 characters for HS512")
        }
        Keys.hmacShaKeyFor(keyString.toByteArray())
    }

    /**
     * Generate an access token for a user
     */
    fun generateAccessToken(user: User, roles: List<String>): String {
        val now = Instant.now()
        val expiry = now.plus(authConfig.jwt.accessTokenExpiration)

        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("roles", roles)
            .claim("type", "access")
            .issuer(authConfig.jwt.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    /**
     * Validate a token and return its claims
     */
    fun validateToken(token: String): Jws<Claims>? {
        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(authConfig.jwt.issuer)
                .build()
                .parseSignedClaims(token)
        } catch (e: ExpiredJwtException) {
            logger.debug("JWT token expired: ${e.message}")
            null
        } catch (e: JwtException) {
            logger.debug("Invalid JWT token: ${e.message}")
            null
        } catch (e: Exception) {
            logger.warn("Error validating JWT token", e)
            null
        }
    }

    /**
     * Extract user ID from a token
     */
    fun getUserIdFromToken(token: String): Long? {
        return validateToken(token)?.payload?.subject?.toLongOrNull()
    }

    /**
     * Extract roles from a token
     */
    @Suppress("UNCHECKED_CAST")
    fun getRolesFromToken(token: String): List<String> {
        return try {
            val claims = validateToken(token)?.payload ?: return emptyList()
            claims.get("roles", List::class.java) as? List<String> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if a token is an access token
     */
    fun isAccessToken(token: String): Boolean {
        return try {
            val claims = validateToken(token)?.payload ?: return false
            claims.get("type", String::class.java) == "access"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get expiration time from token
     */
    fun getExpirationFromToken(token: String): Instant? {
        return try {
            validateToken(token)?.payload?.expiration?.toInstant()
        } catch (e: Exception) {
            null
        }
    }
}
