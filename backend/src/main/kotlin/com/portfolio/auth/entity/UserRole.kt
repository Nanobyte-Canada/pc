package com.portfolio.auth.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "user_roles")
class UserRole(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    val role: Role,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    val grantedBy: User? = null,

    @Column(name = "granted_at", nullable = false, updatable = false)
    val grantedAt: OffsetDateTime = OffsetDateTime.now()
)
