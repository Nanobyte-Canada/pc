package com.portfolio.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
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

    @Column(name = "exchange", nullable = false, length = 20)
    var exchange: String,

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

    @Column(name = "asset_class", length = 50)
    var assetClass: String? = null,

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

    // ========================================
    // Alpha Vantage enrichment tracking columns
    // ========================================
    @Column(name = "av_enrichment_status", length = 20)
    @Enumerated(EnumType.STRING)
    var avEnrichmentStatus: AVEnrichmentStatus = AVEnrichmentStatus.PENDING,

    @Column(name = "av_last_attempt_at")
    var avLastAttemptAt: OffsetDateTime? = null,

    @Column(name = "av_last_success_at")
    var avLastSuccessAt: OffsetDateTime? = null,

    @Column(name = "av_error_code", length = 20)
    var avErrorCode: String? = null,

    @Column(name = "av_error_message", columnDefinition = "TEXT")
    var avErrorMessage: String? = null,

    @Column(name = "av_retry_count")
    var avRetryCount: Int = 0,

    @Column(name = "av_raw_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var avRawPayload: String? = null,

    // ========================================
    // Alpha Vantage ETF_PROFILE data fields
    // ========================================
    @Column(name = "av_asset_type", length = 50)
    var avAssetType: String? = null,

    @Column(name = "av_description", columnDefinition = "TEXT")
    var avDescription: String? = null,

    @Column(name = "av_net_assets", precision = 20, scale = 2)
    var avNetAssets: BigDecimal? = null,

    @Column(name = "av_net_expense_ratio", precision = 18, scale = 6)
    var avNetExpenseRatio: BigDecimal? = null,

    @Column(name = "av_portfolio_turnover", precision = 18, scale = 6)
    var avPortfolioTurnover: BigDecimal? = null,

    @Column(name = "av_dividend_yield", precision = 18, scale = 6)
    var avDividendYield: BigDecimal? = null,

    @Column(name = "av_inception_date")
    var avInceptionDate: LocalDate? = null,

    @Column(name = "av_is_leveraged")
    var avIsLeveraged: Boolean = false,

    @Column(name = "av_holdings_count")
    var avHoldingsCount: Int? = null,

    @Column(name = "av_holdings_as_of_date")
    var avHoldingsAsOfDate: LocalDate? = null,

    // Sector allocations (11 GICS sectors as decimals 0.0 to 1.0)
    @Column(name = "av_sector_info_tech", precision = 18, scale = 6)
    var avSectorInfoTech: BigDecimal? = null,

    @Column(name = "av_sector_comm_services", precision = 18, scale = 6)
    var avSectorCommServices: BigDecimal? = null,

    @Column(name = "av_sector_consumer_disc", precision = 18, scale = 6)
    var avSectorConsumerDisc: BigDecimal? = null,

    @Column(name = "av_sector_consumer_staples", precision = 18, scale = 6)
    var avSectorConsumerStaples: BigDecimal? = null,

    @Column(name = "av_sector_healthcare", precision = 18, scale = 6)
    var avSectorHealthcare: BigDecimal? = null,

    @Column(name = "av_sector_industrials", precision = 18, scale = 6)
    var avSectorIndustrials: BigDecimal? = null,

    @Column(name = "av_sector_utilities", precision = 18, scale = 6)
    var avSectorUtilities: BigDecimal? = null,

    @Column(name = "av_sector_materials", precision = 18, scale = 6)
    var avSectorMaterials: BigDecimal? = null,

    @Column(name = "av_sector_energy", precision = 18, scale = 6)
    var avSectorEnergy: BigDecimal? = null,

    @Column(name = "av_sector_financials", precision = 18, scale = 6)
    var avSectorFinancials: BigDecimal? = null,

    @Column(name = "av_sector_real_estate", precision = 18, scale = 6)
    var avSectorRealEstate: BigDecimal? = null,

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

    // ========================================
    // etf.com enrichment data: Summary
    // ========================================
    @Column(name = "etfcom_issuer", length = 100)
    var etfcomIssuer: String? = null,

    @Column(name = "etfcom_inception_date")
    var etfcomInceptionDate: LocalDate? = null,

    @Column(name = "etfcom_expense_ratio", precision = 18, scale = 6)
    var etfcomExpenseRatio: BigDecimal? = null,

    @Column(name = "etfcom_aum", precision = 20, scale = 2)
    var etfcomAum: BigDecimal? = null,

    @Column(name = "etfcom_index_tracked", length = 255)
    var etfcomIndexTracked: String? = null,

    @Column(name = "etfcom_segment", length = 255)
    var etfcomSegment: String? = null,

    @Column(name = "etfcom_description", columnDefinition = "TEXT")
    var etfcomDescription: String? = null,

    // ========================================
    // etf.com enrichment data: Portfolio
    // ========================================
    @Column(name = "etfcom_weighted_avg_market_cap", precision = 20, scale = 2)
    var etfcomWeightedAvgMarketCap: BigDecimal? = null,

    @Column(name = "etfcom_pe_ratio", precision = 18, scale = 6)
    var etfcomPeRatio: BigDecimal? = null,

    @Column(name = "etfcom_pb_ratio", precision = 18, scale = 6)
    var etfcomPbRatio: BigDecimal? = null,

    @Column(name = "etfcom_dividend_yield", precision = 18, scale = 6)
    var etfcomDividendYield: BigDecimal? = null,

    // ========================================
    // etf.com enrichment data: Performance (NAV)
    // ========================================
    @Column(name = "etfcom_return_1m_nav", precision = 18, scale = 6)
    var etfcomReturn1mNav: BigDecimal? = null,

    @Column(name = "etfcom_return_3m_nav", precision = 18, scale = 6)
    var etfcomReturn3mNav: BigDecimal? = null,

    @Column(name = "etfcom_return_ytd_nav", precision = 18, scale = 6)
    var etfcomReturnYtdNav: BigDecimal? = null,

    @Column(name = "etfcom_return_1y_nav", precision = 18, scale = 6)
    var etfcomReturn1yNav: BigDecimal? = null,

    @Column(name = "etfcom_return_3y_nav", precision = 18, scale = 6)
    var etfcomReturn3yNav: BigDecimal? = null,

    @Column(name = "etfcom_return_5y_nav", precision = 18, scale = 6)
    var etfcomReturn5yNav: BigDecimal? = null,

    // ========================================
    // etf.com enrichment data: Performance (Price)
    // ========================================
    @Column(name = "etfcom_return_1m_price", precision = 18, scale = 6)
    var etfcomReturn1mPrice: BigDecimal? = null,

    @Column(name = "etfcom_return_3m_price", precision = 18, scale = 6)
    var etfcomReturn3mPrice: BigDecimal? = null,

    @Column(name = "etfcom_return_ytd_price", precision = 18, scale = 6)
    var etfcomReturnYtdPrice: BigDecimal? = null,

    @Column(name = "etfcom_return_1y_price", precision = 18, scale = 6)
    var etfcomReturn1yPrice: BigDecimal? = null,

    @Column(name = "etfcom_return_3y_price", precision = 18, scale = 6)
    var etfcomReturn3yPrice: BigDecimal? = null,

    @Column(name = "etfcom_return_5y_price", precision = 18, scale = 6)
    var etfcomReturn5yPrice: BigDecimal? = null,

    // ========================================
    // etf.com enrichment data: Performance date
    // ========================================
    @Column(name = "etfcom_performance_as_of_date")
    var etfcomPerformanceAsOfDate: LocalDate? = null,

    // ========================================
    // etf.com enrichment data: Holdings metadata
    // ========================================
    @Column(name = "etfcom_holdings_count")
    var etfcomHoldingsCount: Int? = null,

    @Column(name = "etfcom_holdings_as_of_date")
    var etfcomHoldingsAsOfDate: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

enum class EtfComEnrichmentStatus {
    PENDING, SUCCESS, FAILED_RETRYABLE, FAILED_PERMANENT, STALE
}
