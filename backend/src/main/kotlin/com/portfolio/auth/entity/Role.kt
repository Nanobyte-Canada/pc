package com.portfolio.auth.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "roles")
class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "name", nullable = false, unique = true, length = 50)
    val name: String,

    @Column(name = "description", length = 255)
    val description: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    companion object {
        const val USER = "USER"
        const val ADMIN = "ADMIN"
    }
}
