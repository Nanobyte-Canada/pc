package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "model_portfolio_allocations")
class ModelPortfolioAllocation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_portfolio_id", nullable = false)
    val modelPortfolio: ModelPortfolio,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "target_percent", nullable = false, precision = 7, scale = 4)
    var targetPercent: BigDecimal,

    @Column(name = "asset_class", length = 50)
    var assetClass: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
