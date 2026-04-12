package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "exchanges", schema = "ingestion")
class Exchange(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(nullable = false, unique = true, length = 10)
    val code: String,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(length = 100)
    var country: String? = null,

    @Column(length = 10)
    var currency: String? = null,

    @Column(name = "operating_mic", length = 50)
    var operatingMic: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
