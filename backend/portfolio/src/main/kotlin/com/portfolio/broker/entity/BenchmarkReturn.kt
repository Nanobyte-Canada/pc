package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "benchmark_returns")
class BenchmarkReturn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "return_date", nullable = false)
    val returnDate: LocalDate,

    @Column(name = "close_price", nullable = false, precision = 18, scale = 6)
    val closePrice: BigDecimal,

    @Column(name = "daily_return", precision = 12, scale = 8)
    val dailyReturn: BigDecimal? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
