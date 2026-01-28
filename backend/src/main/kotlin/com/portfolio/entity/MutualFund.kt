package com.portfolio.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class MutualFundStatus {
    ACTIVE, CLOSED, SUSPENDED, PENDING
}

@Entity
@Table(name = "mutual_funds")
class MutualFund(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "symbol", nullable = false, length = 20)
    var symbol: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "isin", length = 12)
    var isin: String? = null,

    @Column(name = "cusip", length = 9)
    var cusip: String? = null,

    @Column(name = "issuer", length = 100)
    var issuer: String? = null,

    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "USD",

    @Column(name = "domicile", nullable = false, length = 3)
    var domicile: String = "USA",

    @Column(name = "inception_date")
    var inceptionDate: LocalDate? = null,

    @Column(name = "expense_ratio", precision = 6, scale = 4)
    var expenseRatio: BigDecimal? = null,

    @Column(name = "fund_type", length = 50)
    var fundType: String? = null,

    @Column(name = "asset_class", length = 50)
    var assetClass: String? = null,

    @Column(name = "minimum_investment", precision = 15, scale = 2)
    var minimumInvestment: BigDecimal? = null,

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: MutualFundStatus = MutualFundStatus.ACTIVE,

    // Ingestion tracking columns
    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "source_last_seen_at")
    var sourceLastSeenAt: OffsetDateTime? = null,

    @Column(name = "raw_eodhd_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var rawEodhdPayload: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
