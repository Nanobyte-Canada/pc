package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "portfolio_targets")
class PortfolioTarget(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: PortfolioGroup,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "target_percent", nullable = false, precision = 7, scale = 4)
    var targetPercent: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
