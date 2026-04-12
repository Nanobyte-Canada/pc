package com.portfolio.auth.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(name = "new_email", length = 255)
    val newEmail: String? = null,

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
