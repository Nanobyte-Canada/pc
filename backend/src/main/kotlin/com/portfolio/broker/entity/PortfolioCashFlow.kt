package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class CashFlowType {
    CONTRIBUTION, WITHDRAWAL, DIVIDEND
}

@Entity
@Table(name = "portfolio_cash_flows")
class PortfolioCashFlow(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: PortfolioGroup,

    @Column(name = "flow_date", nullable = false)
    val flowDate: LocalDate,

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 20)
    val flowType: CashFlowType,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "CAD",

    @Column(name = "source", length = 50)
    val source: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
