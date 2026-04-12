package com.portfolio.auth.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(name = "device_info", length = 255)
    val deviceInfo: String? = null,

    @Column(name = "ip_address", length = 45)
    val ipAddress: String? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,

    @Column(name = "revoked_at")
    var revokedAt: OffsetDateTime? = null,

    @Column(name = "revoked_reason", length = 100)
    var revokedReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun isValid(): Boolean {
        return revokedAt == null && expiresAt.isAfter(OffsetDateTime.now())
    }

    fun revoke(reason: String) {
        this.revokedAt = OffsetDateTime.now()
        this.revokedReason = reason
    }
}
