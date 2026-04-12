package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class RebalanceFrequency {
    MANUAL, MONTHLY, QUARTERLY, SEMI_ANNUALLY, ANNUALLY
}

@Entity
@Table(name = "portfolio_group_settings")
class PortfolioGroupSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: PortfolioGroup,

    @Column(name = "sell_to_rebalance", nullable = false)
    var sellToRebalance: Boolean = false,

    @Column(name = "keep_currencies_separate", nullable = false)
    var keepCurrenciesSeparate: Boolean = false,

    @Column(name = "prevent_non_tradable_trades", nullable = false)
    var preventNonTradableTrades: Boolean = false,

    @Column(name = "notify_new_assets", nullable = false)
    var notifyNewAssets: Boolean = true,

    @Column(name = "retain_cash_for_exchange", nullable = false)
    var retainCashForExchange: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "rebalance_frequency", nullable = false, length = 20)
    var rebalanceFrequency: RebalanceFrequency = RebalanceFrequency.MANUAL,

    @Column(name = "accuracy_threshold", nullable = false, precision = 5, scale = 2)
    var accuracyThreshold: BigDecimal = BigDecimal("90.00"),

    @Column(name = "auto_execute", nullable = false)
    var autoExecute: Boolean = false,

    @Column(name = "last_rebalanced_at")
    var lastRebalancedAt: OffsetDateTime? = null,

    @Column(name = "next_rebalance_date")
    var nextRebalanceDate: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
