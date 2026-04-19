package com.portfolio.broker.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

// ========== Dashboard Preferences ==========

data class WidgetPreferenceDto(
    val key: String,
    val visible: Boolean,
    val sortOrder: Int,
    val columnSpan: Int
)

data class DashboardPreferencesResponse(
    val widgets: List<WidgetPreferenceDto>
)

data class UpdateDashboardPreferencesRequest(
    val widgets: List<WidgetPreferenceDto>
)

// ========== Dashboard Summary ==========

data class PortfolioValueDto(
    val totalValue: BigDecimal,
    val investmentValue: BigDecimal,
    val cashValue: BigDecimal,
    val totalChange: BigDecimal?,
    val totalChangePercent: BigDecimal?,
    val currency: String = "CAD"
)

data class PositionsSummaryDto(
    val stocks: Int,
    val etfs: Int,
    val mutualFunds: Int,
    val options: Int,
    val bonds: Int,
    val cash: Int,
    val other: Int,
    val total: Int
)

data class HoldingsCountDto(
    val directStocks: Int,
    val lookThroughStocks: Int,
    val totalUniqueHoldings: Int,
    val etfsDecomposed: Int,
    val mutualFundsDecomposed: Int,
    val coveragePercent: BigDecimal
)

data class DashboardSummaryResponse(
    val portfolioValue: PortfolioValueDto,
    val positionsSummary: PositionsSummaryDto,
    val holdingsCount: HoldingsCountDto,
    val warnings: List<String> = emptyList()
)

// ========== Cash & Buying Power ==========

data class CurrencyAmountDto(
    val currency: String,
    val amount: BigDecimal
)

data class DashboardCashResponse(
    val availableCash: List<CurrencyAmountDto>,
    val buyingPower: List<CurrencyAmountDto>,
    val totalCashCAD: BigDecimal,
    val totalBuyingPowerCAD: BigDecimal = BigDecimal.ZERO
)

// ========== Sector Exposure ==========

data class IndustryGroupExposureDto(
    val code: String,
    val name: String,
    val weight: BigDecimal
)

data class SectorExposureDto(
    val sectorCode: String,
    val sectorName: String,
    val weight: BigDecimal,
    val industryGroups: List<IndustryGroupExposureDto>
)

data class SectorExposureResponse(
    val sectors: List<SectorExposureDto>,
    val coveragePercent: BigDecimal,
    val unmappedWeight: BigDecimal,
    val warnings: List<String> = emptyList()
)

// ========== Geography Exposure ==========

data class CountryExposureDto(
    val code: String,
    val name: String,
    val weight: BigDecimal
)

data class RegionExposureDto(
    val name: String,
    val weight: BigDecimal,
    val countries: List<CountryExposureDto>
)

data class GeographyExposureResponse(
    val regions: List<RegionExposureDto>,
    val coveragePercent: BigDecimal,
    val unmappedWeight: BigDecimal,
    val warnings: List<String> = emptyList()
)

// ========== Risk Profile ==========

data class RiskFactorsDto(
    val concentrationHHI: BigDecimal,
    val top10Concentration: BigDecimal,
    val sectorConcentrationHHI: BigDecimal,
    val geographicConcentration: BigDecimal,
    val assetTypeDistribution: Map<String, BigDecimal>
)

data class RiskProfileResponse(
    val riskScore: Int,
    val riskLevel: String,
    val factors: RiskFactorsDto
)

// ========== Open Orders ==========

data class OpenOrderDto(
    val id: Long,
    val symbol: String,
    val action: String,
    val requestedUnits: BigDecimal,
    val requestedPrice: BigDecimal?,
    val limitPrice: BigDecimal?,
    val status: String,
    val orderType: String,
    val accountName: String?,
    val createdAt: OffsetDateTime
)

data class OpenOrdersResponse(
    val orders: List<OpenOrderDto>,
    val totalCount: Int
)

// ========== Fees & Commission ==========

data class MonthlyFeeDto(
    val month: String,
    val fees: BigDecimal,
    val commissions: BigDecimal
)

data class FeesResponse(
    val last12Months: FeesTotalDto,
    val monthlyBreakdown: List<MonthlyFeeDto>,
    val managementExpensePerMonth: BigDecimal
)

data class FeesTotalDto(
    val totalFees: BigDecimal,
    val totalCommissions: BigDecimal,
    val totalManagementExpense: BigDecimal,
    val total: BigDecimal
)

// ========== Dividend Calendar ==========

data class DividendEntryDto(
    val date: LocalDate,
    val symbol: String?,
    val amount: BigDecimal,
    val currency: String,
    val accountName: String?,
    val type: String = "DIVIDEND"
)

data class DividendCalendarResponse(
    val month: String,
    val totalDividends: BigDecimal,
    val entries: List<DividendEntryDto>
)

// ========== Holdings Table ==========

data class LookThroughHoldingDto(
    val symbol: String,
    val name: String?,
    val effectiveWeight: BigDecimal,
    val sector: String?,
    val industryGroup: String?,
    val country: String?,
    val sources: List<HoldingSourceDto>
)

data class HoldingSourceDto(
    val type: String,
    val instrumentSymbol: String?,
    val contribution: BigDecimal
)

data class HoldingsTableResponse(
    val holdings: List<LookThroughHoldingDto>,
    val totalCount: Int,
    val coveragePercent: BigDecimal
)

// ========== Connected Accounts ==========

data class LinkedGroupInfoDto(
    val id: Long,
    val name: String,
    val accuracy: BigDecimal
)

data class DashboardAccountDto(
    val connectionId: Long,
    val brokerName: String,
    val brokerLogoUrl: String?,
    val accountName: String?,
    val accountType: String?,
    val accountNumber: String?,
    val status: String,
    val totalValue: BigDecimal?,
    val investmentValue: BigDecimal?,
    val cash: BigDecimal?,
    val buyingPower: BigDecimal?,
    val positionsCount: Int,
    val lastFetchedAt: OffsetDateTime?,
    val linkedGroup: LinkedGroupInfoDto?,
    val modelPortfolioId: Long? = null,
    val modelPortfolioName: String? = null,
    val needsSetup: Boolean
)

data class DashboardAccountsResponse(
    val accounts: List<DashboardAccountDto>
)

// ========== Refresh ==========

data class RefreshAllResponse(
    val connectionsRefreshed: Int,
    val message: String
)

// ========== IRR (Internal Rate of Return) ==========

data class AccountIrrDto(
    val connectionId: Long,
    val brokerName: String?,
    val accountName: String?,
    val irr: BigDecimal?,
    val startDate: String?,
    val endDate: String?
)

data class DashboardIrrResponse(
    val portfolioIrr: BigDecimal?,
    val accounts: List<AccountIrrDto>
)
