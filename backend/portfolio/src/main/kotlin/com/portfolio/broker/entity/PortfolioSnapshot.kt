package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "portfolio_snapshots")
class PortfolioSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: PortfolioGroup,

    @Column(name = "snapshot_date", nullable = false)
    val snapshotDate: LocalDate,

    @Column(name = "total_value", nullable = false, precision = 18, scale = 2)
    val totalValue: BigDecimal,

    @Column(name = "positions", nullable = false, columnDefinition = "JSONB")
    val positions: String,

    @Column(name = "cash", nullable = false, columnDefinition = "JSONB")
    val cash: String,

    @Column(name = "accuracy", precision = 5, scale = 2)
    val accuracy: BigDecimal? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
