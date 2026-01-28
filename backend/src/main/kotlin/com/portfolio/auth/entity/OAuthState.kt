package com.portfolio.auth.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "oauth_states")
class OAuthState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    val stateHash: String,

    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,

    @Column(name = "redirect_uri", length = 500)
    val redirectUri: String? = null,

    @Column(name = "code_verifier", length = 128)
    val codeVerifier: String? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,

    @Column(name = "used_at")
    var usedAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun isValid(): Boolean {
        return usedAt == null && expiresAt.isAfter(OffsetDateTime.now())
    }

    fun markUsed() {
        this.usedAt = OffsetDateTime.now()
    }
}
