package com.portfolio.auth.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

enum class UserStatus {
    ACTIVE, INACTIVE, SUSPENDED, DELETED
}

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "email_verified_at")
    var emailVerifiedAt: OffsetDateTime? = null,

    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,

    @Column(name = "name", length = 255)
    var name: String? = null,

    @Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "failed_login_attempts", nullable = false)
    var failedLoginAttempts: Int = 0,

    @Column(name = "locked_until")
    var lockedUntil: OffsetDateTime? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: OffsetDateTime? = null,

    @Column(name = "last_login_ip", length = 45)
    var lastLoginIp: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val identities: MutableList<UserIdentity> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val userRoles: MutableList<UserRole> = mutableListOf()
) {
    fun isLocked(): Boolean {
        return lockedUntil != null && lockedUntil!!.isAfter(OffsetDateTime.now())
    }

    fun hasPassword(): Boolean {
        return passwordHash != null
    }

    fun getRoleNames(): List<String> {
        return userRoles.map { it.role.name }
    }
}
