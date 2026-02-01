package com.portfolio.broker.entity

import jakarta.persistence.*
import jakarta.persistence.FetchType.LAZY
import java.time.OffsetDateTime

@Entity
@Table(name = "connection_tokens")
class ConnectionToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false, unique = true)
    val connection: BrokerConnection,

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    var accessTokenEncrypted: String,

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    var refreshTokenEncrypted: String? = null,

    @Column(name = "token_type", length = 50)
    var tokenType: String = "Bearer",

    @Column(name = "scope", length = 500)
    var scope: String? = null,

    @Column(name = "api_server_url", length = 255)
    var apiServerUrl: String? = null,

    @Column(name = "expires_at")
    var expiresAt: OffsetDateTime? = null,

    @Column(name = "last_refreshed_at")
    var lastRefreshedAt: OffsetDateTime? = null,

    @Column(name = "refresh_count", nullable = false)
    var refreshCount: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun isExpired(): Boolean {
        return expiresAt != null && expiresAt!!.isBefore(OffsetDateTime.now())
    }

    fun isExpiringSoon(thresholdMinutes: Long = 5): Boolean {
        if (expiresAt == null) return false
        return expiresAt!!.isBefore(OffsetDateTime.now().plusMinutes(thresholdMinutes))
    }

    fun updateTokens(
        accessToken: String,
        refreshToken: String?,
        expiresIn: Long?,
        apiServerUrl: String? = null
    ) {
        this.accessTokenEncrypted = accessToken
        this.refreshTokenEncrypted = refreshToken
        this.expiresAt = expiresIn?.let { OffsetDateTime.now().plusSeconds(it) }
        this.apiServerUrl = apiServerUrl ?: this.apiServerUrl
        this.lastRefreshedAt = OffsetDateTime.now()
        this.refreshCount++
    }
}
