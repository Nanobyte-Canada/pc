package com.portfolio.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "countries")
class Country(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code", nullable = false, length = 3)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "alpha2_code", length = 2)
    val alpha2Code: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    val region: Region,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
