package com.portfolio.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "data_sources")
class DataSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code", nullable = false, length = 50)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
