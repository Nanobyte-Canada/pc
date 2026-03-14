package com.portfolio.broker.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

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

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
