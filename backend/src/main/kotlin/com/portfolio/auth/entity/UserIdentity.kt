package com.portfolio.auth.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "user_identities")
class UserIdentity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,

    @Column(name = "provider_user_id", nullable = false, length = 255)
    val providerUserId: String,

    @Column(name = "provider_email", length = 255)
    var providerEmail: String? = null,

    @Column(name = "provider_name", length = 255)
    var providerName: String? = null,

    @Column(name = "provider_avatar_url", length = 500)
    var providerAvatarUrl: String? = null,

    @Column(name = "access_token_encrypted", length = 1000)
    var accessTokenEncrypted: String? = null,

    @Column(name = "refresh_token_encrypted", length = 1000)
    var refreshTokenEncrypted: String? = null,

    @Column(name = "token_expires_at")
    var tokenExpiresAt: OffsetDateTime? = null,

    @Column(name = "raw_profile", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var rawProfile: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    companion object {
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_GITHUB = "github"
    }
}
