package com.portfolio.entity.gics

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "gics_industries")
class GicsIndustry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code", nullable = false, length = 6)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_group_id", nullable = false)
    val industryGroup: GicsIndustryGroup,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @OneToMany(mappedBy = "industry", fetch = FetchType.LAZY)
    val subIndustries: MutableList<GicsSubIndustry> = mutableListOf()
)
