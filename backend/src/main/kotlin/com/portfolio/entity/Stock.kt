package com.portfolio.entity

import com.portfolio.entity.gics.GicsSubIndustry
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "stocks")
class Stock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "ticker", nullable = false, length = 20)
    var ticker: String,

    @Column(name = "exchange", nullable = false, length = 20)
    var exchange: String,

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gics_sub_industry_id")
    var gicsSubIndustry: GicsSubIndustry? = null,

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
    // Alpha Vantage OVERVIEW data fields
    // ========================================
    @Column(name = "av_asset_type", length = 50)
    var avAssetType: String? = null,

    @Column(name = "av_description", columnDefinition = "TEXT")
    var avDescription: String? = null,

    @Column(name = "av_cik", length = 20)
    var avCik: String? = null,

    @Column(name = "av_sector", length = 100)
    var avSector: String? = null,

    @Column(name = "av_industry", length = 150)
    var avIndustry: String? = null,

    @Column(name = "av_address", length = 255)
    var avAddress: String? = null,

    @Column(name = "av_official_site", length = 255)
    var avOfficialSite: String? = null,

    @Column(name = "av_fiscal_year_end", length = 20)
    var avFiscalYearEnd: String? = null,

    @Column(name = "av_latest_quarter")
    var avLatestQuarter: LocalDate? = null,

    // Financial metrics
    @Column(name = "av_market_cap", precision = 20, scale = 2)
    var avMarketCap: BigDecimal? = null,

    @Column(name = "av_ebitda", precision = 20, scale = 2)
    var avEbitda: BigDecimal? = null,

    @Column(name = "av_pe_ratio", precision = 12, scale = 4)
    var avPeRatio: BigDecimal? = null,

    @Column(name = "av_peg_ratio", precision = 12, scale = 4)
    var avPegRatio: BigDecimal? = null,

    @Column(name = "av_book_value", precision = 15, scale = 4)
    var avBookValue: BigDecimal? = null,

    @Column(name = "av_dividend_per_share", precision = 12, scale = 4)
    var avDividendPerShare: BigDecimal? = null,

    @Column(name = "av_dividend_yield", precision = 18, scale = 6)
    var avDividendYield: BigDecimal? = null,

    @Column(name = "av_eps", precision = 12, scale = 4)
    var avEps: BigDecimal? = null,

    @Column(name = "av_revenue_per_share_ttm", precision = 15, scale = 4)
    var avRevenuePerShareTtm: BigDecimal? = null,

    @Column(name = "av_profit_margin", precision = 18, scale = 6)
    var avProfitMargin: BigDecimal? = null,

    @Column(name = "av_operating_margin_ttm", precision = 18, scale = 6)
    var avOperatingMarginTtm: BigDecimal? = null,

    @Column(name = "av_return_on_assets_ttm", precision = 18, scale = 6)
    var avReturnOnAssetsTtm: BigDecimal? = null,

    @Column(name = "av_return_on_equity_ttm", precision = 18, scale = 6)
    var avReturnOnEquityTtm: BigDecimal? = null,

    @Column(name = "av_revenue_ttm", precision = 20, scale = 2)
    var avRevenueTtm: BigDecimal? = null,

    @Column(name = "av_gross_profit_ttm", precision = 20, scale = 2)
    var avGrossProfitTtm: BigDecimal? = null,

    @Column(name = "av_quarterly_earnings_growth_yoy", precision = 18, scale = 6)
    var avQuarterlyEarningsGrowthYoy: BigDecimal? = null,

    @Column(name = "av_quarterly_revenue_growth_yoy", precision = 18, scale = 6)
    var avQuarterlyRevenueGrowthYoy: BigDecimal? = null,

    // Analyst ratings
    @Column(name = "av_analyst_target_price", precision = 15, scale = 4)
    var avAnalystTargetPrice: BigDecimal? = null,

    @Column(name = "av_analyst_rating_strong_buy")
    var avAnalystRatingStrongBuy: Int? = null,

    @Column(name = "av_analyst_rating_buy")
    var avAnalystRatingBuy: Int? = null,

    @Column(name = "av_analyst_rating_hold")
    var avAnalystRatingHold: Int? = null,

    @Column(name = "av_analyst_rating_sell")
    var avAnalystRatingSell: Int? = null,

    @Column(name = "av_analyst_rating_strong_sell")
    var avAnalystRatingStrongSell: Int? = null,

    // Price metrics
    @Column(name = "av_trailing_pe", precision = 12, scale = 4)
    var avTrailingPe: BigDecimal? = null,

    @Column(name = "av_forward_pe", precision = 12, scale = 4)
    var avForwardPe: BigDecimal? = null,

    @Column(name = "av_52_week_high", precision = 15, scale = 4)
    var av52WeekHigh: BigDecimal? = null,

    @Column(name = "av_52_week_low", precision = 15, scale = 4)
    var av52WeekLow: BigDecimal? = null,

    @Column(name = "av_50_day_ma", precision = 15, scale = 4)
    var av50DayMa: BigDecimal? = null,

    @Column(name = "av_200_day_ma", precision = 15, scale = 4)
    var av200DayMa: BigDecimal? = null,

    @Column(name = "av_shares_outstanding")
    var avSharesOutstanding: Long? = null,

    @Column(name = "av_beta", precision = 10, scale = 4)
    var avBeta: BigDecimal? = null,

    // Valuation ratios
    @Column(name = "av_price_to_sales_ratio_ttm", precision = 12, scale = 4)
    var avPriceToSalesRatioTtm: BigDecimal? = null,

    @Column(name = "av_price_to_book_ratio", precision = 12, scale = 4)
    var avPriceToBookRatio: BigDecimal? = null,

    @Column(name = "av_ev_to_revenue", precision = 12, scale = 4)
    var avEvToRevenue: BigDecimal? = null,

    @Column(name = "av_ev_to_ebitda", precision = 12, scale = 4)
    var avEvToEbitda: BigDecimal? = null,

    @Column(name = "av_diluted_eps_ttm", precision = 12, scale = 4)
    var avDilutedEpsTtm: BigDecimal? = null,

    // Shares & Ownership
    @Column(name = "av_shares_float")
    var avSharesFloat: Long? = null,

    @Column(name = "av_percent_insiders", precision = 18, scale = 6)
    var avPercentInsiders: BigDecimal? = null,

    @Column(name = "av_percent_institutions", precision = 18, scale = 6)
    var avPercentInstitutions: BigDecimal? = null,

    // Dividend dates
    @Column(name = "av_dividend_date")
    var avDividendDate: LocalDate? = null,

    @Column(name = "av_ex_dividend_date")
    var avExDividendDate: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)

enum class SecurityStatus {
    ACTIVE, DELISTED, SUSPENDED, PENDING
}

/**
 * Alpha Vantage enrichment status values.
 */
enum class AVEnrichmentStatus {
    PENDING,
    SUCCESS,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    STALE
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
