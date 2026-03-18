package com.portfolio.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "etfs")
class Etf(
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

    @Column(name = "asset_class", length = 50)
    var assetClass: String? = null,

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: SecurityStatus = SecurityStatus.ACTIVE,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "source_last_seen_at")
    var sourceLastSeenAt: OffsetDateTime? = null,

    // ========================================
    // etf.com universe fields
    // ========================================
    @Column(name = "etfcom_fund_id")
    var etfcomFundId: Int? = null,

    @Column(name = "etfcom_asset_class", length = 50)
    var etfcomAssetClass: String? = null,

    // ========================================
    // etf.com enrichment tracking
    // ========================================
    @Column(name = "etfcom_enrichment_status", length = 20)
    @Enumerated(EnumType.STRING)
    var etfcomEnrichmentStatus: EtfComEnrichmentStatus = EtfComEnrichmentStatus.PENDING,

    @Column(name = "etfcom_last_attempt_at")
    var etfcomLastAttemptAt: OffsetDateTime? = null,

    @Column(name = "etfcom_last_success_at")
    var etfcomLastSuccessAt: OffsetDateTime? = null,

    @Column(name = "etfcom_retry_count")
    var etfcomRetryCount: Int = 0,

    @Column(name = "etfcom_error_code", length = 50)
    var etfcomErrorCode: String? = null,

    @Column(name = "etfcom_error_message", columnDefinition = "TEXT")
    var etfcomErrorMessage: String? = null,

    @Column(name = "etfcom_raw_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var etfcomRawPayload: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

enum class EtfComEnrichmentStatus {
    PENDING, SUCCESS, FAILED_RETRYABLE, FAILED_PERMANENT, STALE
}
