package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import jakarta.persistence.FetchType.LAZY
import java.time.OffsetDateTime

@Entity
@Table(name = "broker_oauth_states")
class BrokerOAuthState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    val stateHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_id", nullable = false)
    val broker: Broker,

    @Column(name = "redirect_uri", length = 500)
    val redirectUri: String? = null,

    @Column(name = "code_verifier", length = 128)
    val codeVerifier: String? = null,

    @Column(name = "nonce", length = 64)
    val nonce: String? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,

    @Column(name = "used_at")
    var usedAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun isExpired(): Boolean = expiresAt.isBefore(OffsetDateTime.now())

    fun isUsed(): Boolean = usedAt != null

    fun isValid(): Boolean = !isExpired() && !isUsed()

    fun markAsUsed() {
        usedAt = OffsetDateTime.now()
    }
}
