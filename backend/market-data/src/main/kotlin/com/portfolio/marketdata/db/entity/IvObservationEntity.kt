package com.portfolio.marketdata.db.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "iv_observations",
    schema = "market_data",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ticker", "observed_date"])]
)
data class IvObservationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 20)
    val ticker: String,

    @Column(name = "atm_iv", nullable = false, precision = 10, scale = 6)
    val atmIv: BigDecimal,

    @Column(name = "observed_date", nullable = false)
    val observedDate: LocalDate
)
