package com.portfolio.marketdata.db.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "underlying_prices", schema = "market_data")
data class UnderlyingPriceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 20)
    val ticker: String,

    @Column(nullable = false, precision = 12, scale = 4)
    val price: BigDecimal,

    @Column
    val volume: Long? = null,

    @Column(name = "observed_at", nullable = false)
    val observedAt: Instant
)
