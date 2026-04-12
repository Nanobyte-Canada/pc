package com.portfolio.broker.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "portfolio_excluded_assets")
class PortfolioExcludedAsset(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: PortfolioGroup,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
