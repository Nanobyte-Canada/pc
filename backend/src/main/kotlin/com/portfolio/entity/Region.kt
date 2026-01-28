package com.portfolio.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "regions")
class Region(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code", nullable = false, length = 20)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @OneToMany(mappedBy = "region", fetch = FetchType.LAZY)
    val countries: MutableList<Country> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
