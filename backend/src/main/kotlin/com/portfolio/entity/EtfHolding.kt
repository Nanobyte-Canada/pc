package com.portfolio.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class ResolutionStatus {
    RESOLVED, UNRESOLVED, PARTIAL
}

enum class HoldingType {
    STOCK, ETF, MUTUAL_FUND, UNKNOWN
}

enum class HoldingSourceSection {
    TOP_TEN, EODHD
}

/**
 * Source of holdings data.
 */
enum class HoldingDataSource {
    EODHD, ALPHA_VANTAGE, MANUAL, ETF_COM
}

@Entity
@Table(name = "etf_holdings")
class EtfHolding(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etf_id", nullable = false)
    val etf: Etf,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    var stock: Stock? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_etf_id")
    var heldEtf: Etf? = null,

    @Column(name = "as_of_date", nullable = false)
    val asOfDate: LocalDate,

    @Column(name = "weight", precision = 8, scale = 6)
    var weight: BigDecimal? = null,

    @Column(name = "shares", precision = 18, scale = 4)
    var shares: BigDecimal? = null,

    @Column(name = "market_value", precision = 18, scale = 2)
    var marketValue: BigDecimal? = null,

    // Raw identifier columns for unresolved holdings
    @Column(name = "raw_ticker", length = 50)
    var rawTicker: String? = null,

    @Column(name = "raw_name", length = 255)
    var rawName: String? = null,

    @Column(name = "raw_isin", length = 20)
    var rawIsin: String? = null,

    @Column(name = "raw_cusip", length = 20)
    var rawCusip: String? = null,

    @Column(name = "raw_country", length = 50)
    var rawCountry: String? = null,

    @Column(name = "resolution_status", length = 20)
    @Enumerated(EnumType.STRING)
    var resolutionStatus: ResolutionStatus = ResolutionStatus.RESOLVED,

    @Column(name = "holding_type", length = 20)
    @Enumerated(EnumType.STRING)
    var holdingType: HoldingType = HoldingType.STOCK,

    @Column(name = "rank")
    var rank: Int? = null,

    @Column(name = "source_section", length = 20)
    @Enumerated(EnumType.STRING)
    var sourceSection: HoldingSourceSection = HoldingSourceSection.EODHD,

    @Column(name = "is_valid_symbol")
    var isValidSymbol: Boolean? = null,

    // Data source tracking
    @Column(name = "data_source", length = 20)
    @Enumerated(EnumType.STRING)
    var dataSource: HoldingDataSource = HoldingDataSource.EODHD,

    // Alpha Vantage specific fields
    @Column(name = "av_weight", precision = 18, scale = 6)
    var avWeight: BigDecimal? = null,

    @Column(name = "av_last_updated_at")
    var avLastUpdatedAt: OffsetDateTime? = null,

    // etf.com specific fields
    @Column(name = "etfcom_weight", precision = 18, scale = 6)
    var etfcomWeight: BigDecimal? = null,

    @Column(name = "etfcom_last_updated_at")
    var etfcomLastUpdatedAt: OffsetDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingestion_batch_id")
    val ingestionBatch: IngestionBatch? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
