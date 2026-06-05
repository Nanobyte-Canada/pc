package com.portfolio.marketdata.db.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "contract_cache",
    schema = "market_data",
    uniqueConstraints = [UniqueConstraint(columnNames = ["symbol", "sec_type", "expiry", "strike", "option_right"])]
)
data class ContractCacheEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 30)
    val symbol: String,

    @Column(name = "con_id", nullable = false)
    val conId: Int,

    @Column(name = "sec_type", nullable = false, length = 10)
    val secType: String,

    @Column(length = 20)
    val exchange: String? = null,

    @Column
    val expiry: LocalDate? = null,

    @Column(precision = 12, scale = 4)
    val strike: BigDecimal? = null,

    @Column(name = "option_right", length = 4)
    val optionRight: String? = null,

    @Column(name = "trading_class", length = 30)
    val tradingClass: String? = null,

    @Column(length = 10)
    val multiplier: String? = null,

    @Column(name = "cached_at", nullable = false)
    val cachedAt: Instant
)
