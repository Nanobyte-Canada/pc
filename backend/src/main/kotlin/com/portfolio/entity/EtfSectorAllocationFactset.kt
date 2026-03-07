package com.portfolio.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(
    name = "etf_sector_allocations_factset",
    uniqueConstraints = [UniqueConstraint(columnNames = ["etf_id", "sector_name", "as_of_date"])]
)
class EtfSectorAllocationFactset(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etf_id", nullable = false)
    val etf: Etf,

    @Column(name = "sector_name", nullable = false, length = 100)
    val sectorName: String,

    @Column(name = "weight", precision = 18, scale = 6)
    val weight: BigDecimal? = null,

    @Column(name = "as_of_date", nullable = false)
    val asOfDate: LocalDate,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
