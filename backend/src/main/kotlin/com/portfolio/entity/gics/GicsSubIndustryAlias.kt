package com.portfolio.entity.gics

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "gics_sub_industry_aliases")
class GicsSubIndustryAlias(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "alias_code", nullable = false, length = 20)
    val aliasCode: String,

    @Column(name = "alias_name", length = 150)
    val aliasName: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gics_sub_industry_id", nullable = false)
    val gicsSubIndustry: GicsSubIndustry,

    @Column(name = "source", nullable = false, length = 30)
    val source: String = "SEEKING_ALPHA",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
