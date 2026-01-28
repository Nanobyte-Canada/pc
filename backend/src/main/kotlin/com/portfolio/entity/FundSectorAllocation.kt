package com.portfolio.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class AllocationParentType {
    ETF, MUTUAL_FUND
}

enum class AllocationSource {
    STOCK, BOND
}

@Entity
@Table(name = "fund_sector_allocations")
class FundSectorAllocation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "parent_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val parentType: AllocationParentType,

    @Column(name = "parent_id", nullable = false)
    val parentId: Long,

    @Column(name = "as_of_date", nullable = false)
    val asOfDate: LocalDate,

    @Column(name = "source", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val source: AllocationSource,

    // Stock sector breakdown (percentages, can exceed 100 for leveraged funds)
    @Column(name = "basic_materials", precision = 8, scale = 5)
    var basicMaterials: BigDecimal = BigDecimal.ZERO,

    @Column(name = "consumer_cyclical", precision = 8, scale = 5)
    var consumerCyclical: BigDecimal = BigDecimal.ZERO,

    @Column(name = "financials", precision = 8, scale = 5)
    var financials: BigDecimal = BigDecimal.ZERO,

    @Column(name = "real_estate", precision = 8, scale = 5)
    var realEstate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "consumer_defensive", precision = 8, scale = 5)
    var consumerDefensive: BigDecimal = BigDecimal.ZERO,

    @Column(name = "healthcare", precision = 8, scale = 5)
    var healthcare: BigDecimal = BigDecimal.ZERO,

    @Column(name = "utilities", precision = 8, scale = 5)
    var utilities: BigDecimal = BigDecimal.ZERO,

    @Column(name = "communication", precision = 8, scale = 5)
    var communication: BigDecimal = BigDecimal.ZERO,

    @Column(name = "energy", precision = 8, scale = 5)
    var energy: BigDecimal = BigDecimal.ZERO,

    @Column(name = "industrials", precision = 8, scale = 5)
    var industrials: BigDecimal = BigDecimal.ZERO,

    @Column(name = "technology", precision = 8, scale = 5)
    var technology: BigDecimal = BigDecimal.ZERO,

    // Bond type breakdown
    @Column(name = "government", precision = 8, scale = 5)
    var government: BigDecimal = BigDecimal.ZERO,

    @Column(name = "municipal", precision = 8, scale = 5)
    var municipal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "corporate", precision = 8, scale = 5)
    var corporate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "securitized", precision = 8, scale = 5)
    var securitized: BigDecimal = BigDecimal.ZERO,

    @Column(name = "derivative", precision = 8, scale = 5)
    var derivative: BigDecimal = BigDecimal.ZERO,

    @Column(name = "cash_and_equiv", precision = 8, scale = 5)
    var cashAndEquiv: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
