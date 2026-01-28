package com.portfolio.entity.gics

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "gics_sub_industries")
class GicsSubIndustry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code", nullable = false, length = 8)
    val code: String,

    @Column(name = "name", nullable = false, length = 150)
    val name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_id", nullable = false)
    val industry: GicsIndustry,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
