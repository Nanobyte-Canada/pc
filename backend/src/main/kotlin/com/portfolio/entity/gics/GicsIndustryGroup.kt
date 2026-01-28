package com.portfolio.entity.gics

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "gics_industry_groups")
class GicsIndustryGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code", nullable = false, length = 4)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id", nullable = false)
    val sector: GicsSector,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @OneToMany(mappedBy = "industryGroup", fetch = FetchType.LAZY)
    val industries: MutableList<GicsIndustry> = mutableListOf()
)
