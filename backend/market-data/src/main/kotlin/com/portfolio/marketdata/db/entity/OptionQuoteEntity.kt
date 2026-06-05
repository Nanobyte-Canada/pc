package com.portfolio.marketdata.db.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "option_quotes", schema = "market_data")
data class OptionQuoteEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 20)
    val ticker: String,

    @Column(nullable = false)
    val expiry: LocalDate,

    @Column(nullable = false, precision = 12, scale = 4)
    val strike: BigDecimal,

    @Column(name = "option_type", nullable = false, length = 4)
    val optionType: String,

    @Column(precision = 12, scale = 4)
    val bid: BigDecimal? = null,

    @Column(precision = 12, scale = 4)
    val ask: BigDecimal? = null,

    @Column(precision = 12, scale = 4)
    val mid: BigDecimal? = null,

    @Column(name = "implied_volatility", precision = 10, scale = 6)
    val impliedVolatility: BigDecimal? = null,

    @Column(precision = 10, scale = 6)
    val delta: BigDecimal? = null,

    @Column(precision = 10, scale = 6)
    val gamma: BigDecimal? = null,

    @Column(precision = 10, scale = 6)
    val theta: BigDecimal? = null,

    @Column(precision = 10, scale = 6)
    val vega: BigDecimal? = null,

    @Column(name = "observed_at", nullable = false)
    val observedAt: Instant
)
