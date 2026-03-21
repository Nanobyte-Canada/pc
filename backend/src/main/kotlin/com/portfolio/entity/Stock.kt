package com.portfolio.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "stocks")
class Stock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "ticker", nullable = false, length = 20)
    var ticker: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "isin", length = 12)
    var isin: String? = null,

    @Column(name = "cusip", length = 9)
    var cusip: String? = null,

    @Column(name = "sedol", length = 7)
    var sedol: String? = null,

    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "USD",

    @Column(name = "country", nullable = false, length = 3)
    var country: String = "USA",

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: SecurityStatus = SecurityStatus.ACTIVE,

    // Ingestion tracking columns
    @Column(name = "exchange_code", length = 20)
    var exchangeCode: String? = null,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "source_last_seen_at")
    var sourceLastSeenAt: OffsetDateTime? = null,

    @Column(name = "raw_eodhd_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var rawEodhdPayload: String? = null,

    // ========================================
    // Alpha Vantage ingestion tracking columns
    // ========================================
    @Column(name = "av_ingestion_status", length = 20)
    @Enumerated(EnumType.STRING)
    var avIngestionStatus: AVIngestionStatus = AVIngestionStatus.PENDING,

    @Column(name = "av_ingestion_last_attempt_at")
    var avIngestionLastAttemptAt: OffsetDateTime? = null,

    @Column(name = "av_ingestion_last_success_at")
    var avIngestionLastSuccessAt: OffsetDateTime? = null,

    @Column(name = "av_ingestion_retry_count")
    var avIngestionRetryCount: Int = 0,

    @Column(name = "av_ingestion_error_code", length = 50)
    var avIngestionErrorCode: String? = null,

    @Column(name = "av_ingestion_error_message", length = 500)
    var avIngestionErrorMessage: String? = null,

    @Column(name = "av_raw_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var avRawPayload: JsonNode? = null,

    @Column(name = "gics_sector_code", length = 10)
    var gicsSectorCode: String? = null,

    @Column(name = "gics_industry_group_code", length = 10)
    var gicsIndustryGroupCode: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun avField(key: String): String? =
        avRawPayload?.path(key)
            ?.takeIf { !it.isNull && !it.isMissingNode }
            ?.asText()
            ?.takeIf { it != "None" && it != "-" && it != "N/A" && it.isNotBlank() }

    fun avDecimal(key: String): BigDecimal? = avField(key)?.let {
        try { BigDecimal(it) } catch (_: NumberFormatException) { null }
    }
}

enum class SecurityStatus {
    ACTIVE, DELISTED, SUSPENDED, PENDING
}

/**
 * Alpha Vantage ingestion status values.
 * Tracks whether raw data has been fetched from the API.
 */
enum class AVIngestionStatus {
    PENDING,            // Not yet attempted
    SUCCESS,            // Raw data fetched and stored
    FAILED_RETRYABLE,   // Failed but can retry
    FAILED_PERMANENT,   // Failed permanently (not found)
    STALE               // Data is stale, needs refresh
}
