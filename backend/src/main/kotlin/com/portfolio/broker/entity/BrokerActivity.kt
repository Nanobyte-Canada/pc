package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "broker_activities")
class BrokerActivity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    val connection: BrokerConnection,

    @Column(name = "external_id", length = 100)
    val externalId: String? = null,

    @Column(nullable = false, length = 50)
    val type: String,

    @Column(length = 20)
    val symbol: String? = null,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(precision = 18, scale = 6)
    val quantity: BigDecimal? = null,

    @Column(precision = 18, scale = 6)
    val price: BigDecimal? = null,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(precision = 18, scale = 4)
    val fee: BigDecimal? = null,

    @Column(length = 3)
    val currency: String = "CAD",

    @Column(name = "trade_date", nullable = false)
    val tradeDate: LocalDate,

    @Column(name = "settlement_date")
    val settlementDate: LocalDate? = null,

    @Column(name = "account_name", length = 100)
    val accountName: String? = null,

    @Column(name = "option_type", length = 20)
    val optionType: String? = null,

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    val rawPayload: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
