package com.portfolio.broker.dto

import com.portfolio.broker.entity.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

// Response DTOs
data class BrokerDto(
    val id: Long? = null,
    val code: String? = null,
    val name: String,
    val slug: String? = null,
    val status: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val url: String? = null,
    val openUrl: String? = null,
    val enabled: Boolean? = null,
    val maintenanceMode: Boolean? = null,
    val isDegraded: Boolean? = null,
    val allowsTrading: Boolean? = null,
    val allowsFractionalUnits: Boolean? = null,
    val hasReporting: Boolean? = null,
    val isRealTimeConnection: Boolean? = null,
    val brokerageType: String? = null,
    val authTypes: List<BrokerAuthTypeDto>? = null
)

data class BrokerAuthTypeDto(
    val type: String,    // "read" or "trade"
    val authType: String // "OAUTH", "SCRAPE", "UNOFFICIAL_API"
)

data class BrokersResponse(
    val brokers: List<BrokerDto>
)

data class BrokerConnectionDto(
    val id: Long,
    val broker: BrokerDto,
    val gatewayConnectionId: String? = null,
    val accountNumber: String?,
    val accountType: String?,
    val accountName: String?,
    val accountNumberActual: String? = null,
    val accountMetaType: String? = null,
    val status: String,
    val lastPositionsFetchedAt: OffsetDateTime?,
    val positionsCount: Int,
    val totalValue: BigDecimal?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime,
    val modelPortfolioId: Long? = null,
    val modelPortfolioName: String? = null,
    val supportedOrderTypes: List<String> = listOf("MARKET", "LIMIT")
)

data class BrokerConnectionsResponse(
    val connections: List<BrokerConnectionDto>
)

data class PositionFetchResponse(
    val fetchId: Long,
    val status: String,
    val message: String
)

data class BrokerPositionDto(
    val id: Long,
    val symbol: String,
    val securityName: String?,
    val instrumentType: String?,
    val quantity: BigDecimal,
    val averageCost: BigDecimal?,
    val currentPrice: BigDecimal?,
    val currentValue: BigDecimal?,
    val totalPnl: BigDecimal?,
    val totalPnlPercent: BigDecimal?,
    val currency: String,
    val strikePrice: BigDecimal? = null,
    val expirationDate: String? = null,
    val optionType: String? = null,
    val underlyingSymbol: String? = null
)

data class ConnectionPositionsResponse(
    val connectionId: Long,
    val broker: String?,
    val accountNumber: String?,
    val asOfDate: String,
    val positions: List<BrokerPositionDto>,
    val summary: PositionsSummary
)

data class PositionsSummary(
    val totalValue: BigDecimal,
    val totalCost: BigDecimal,
    val totalPnl: BigDecimal,
    val totalPnlPercent: BigDecimal
)

data class AggregatedPositionDto(
    val symbol: String,
    val securityName: String?,
    val instrumentType: String?,
    val totalQuantity: BigDecimal,
    val totalValue: BigDecimal,
    val averageCost: BigDecimal?,
    val totalPnl: BigDecimal?,
    val totalPnlPercent: BigDecimal?,
    val currency: String,
    val brokerBreakdown: List<BrokerBreakdownDto>
)

data class BrokerBreakdownDto(
    val broker: String?,
    val accountNumber: String?,
    val accountType: String?,
    val quantity: BigDecimal,
    val value: BigDecimal?
)

data class AggregatedPositionsResponse(
    val asOfDate: String,
    val positions: List<AggregatedPositionDto>,
    val aggregateSummary: AggregateSummary
)

data class AggregateSummary(
    val totalValue: BigDecimal,
    val totalCost: BigDecimal,
    val totalPnl: BigDecimal,
    val totalPnlPercent: BigDecimal,
    val brokerCount: Int,
    val accountCount: Int
)

// Mappers
fun Broker.toDto() = BrokerDto(
    id = id,
    code = code,
    name = name,
    slug = code.lowercase(),
    status = status.name,
    logoUrl = logoUrl,
    description = description
)

fun BrokerConnection.toDto(): BrokerConnectionDto {
    val brokerSlug = connectionType?.lowercase() ?: ""
    val brokerNameLower = (brokerName ?: "").lowercase()

    val orderTypes = when {
        brokerSlug == "questrade" || brokerNameLower.contains("questrade") ->
            listOf("MARKET", "LIMIT", "STOP", "STOP_LIMIT")
        brokerSlug == "ibkr" || brokerSlug == "interactive_brokers" ||
            brokerNameLower.contains("interactive") || brokerNameLower.contains("ibkr") ->
            listOf("MARKET", "LIMIT", "STOP", "STOP_LIMIT")
        brokerSlug == "wealthsimple" || brokerNameLower.contains("wealthsimple") ->
            listOf("MARKET", "LIMIT")
        else -> listOf("MARKET", "LIMIT")
    }

    return BrokerConnectionDto(
        id = id,
        broker = BrokerDto(
            name = brokerName ?: connectionType ?: "Unknown Broker",
            slug = connectionType?.lowercase(),
            logoUrl = brokerLogoUrl,
            description = null
        ),
        gatewayConnectionId = gatewayConnectionId,
        accountNumber = accountNumber,
        accountType = accountType,
        accountName = accountName,
        accountNumberActual = accountNumberActual,
        accountMetaType = accountMetaType,
        status = status.name,
        lastPositionsFetchedAt = lastPositionsFetchedAt,
        positionsCount = positionsCount,
        totalValue = totalValue,
        errorMessage = connectionErrorMessage,
        createdAt = createdAt,
        modelPortfolioId = modelPortfolio?.id,
        modelPortfolioName = modelPortfolio?.name,
        supportedOrderTypes = orderTypes
    )
}

fun BrokerPosition.toDto() = BrokerPositionDto(
    id = id,
    symbol = symbol,
    securityName = securityName,
    instrumentType = instrumentType?.name,
    quantity = quantity,
    averageCost = averageCost,
    currentPrice = currentPrice,
    currentValue = currentValue,
    totalPnl = totalPnl,
    totalPnlPercent = totalPnlPercent,
    currency = currency,
    strikePrice = strikePrice,
    expirationDate = expirationDate?.toString(),
    optionType = optionType,
    underlyingSymbol = underlyingSymbol
)

data class ConnectionSyncResponse(
    val syncedCount: Int,
    val message: String
)

// ========== Activity DTOs ==========

data class BrokerActivityDto(
    val id: Long,
    val type: String,
    val symbol: String?,
    val description: String?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val currency: String,
    val tradeDate: String,
    val settlementDate: String?,
    val accountName: String?,
    val optionType: String?
)

data class ActivitiesResponse(
    val activities: List<BrokerActivityDto>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int
)

// ========== Balance DTOs ==========

data class BalanceSnapshotDto(
    val totalValue: BigDecimal?,
    val cash: Map<String, BigDecimal>,
    val currency: String,
    val asOfDate: String
)

data class BalanceHistoryResponse(
    val snapshots: List<BalanceSnapshotDto>,
    val connectionId: Long
)

// ========== Reporting DTOs ==========

data class ReportingPerformanceResponse(
    val contributionsWithdrawals: List<PeriodSummary>,
    val totalValueHistory: List<ValuePoint>,
    val dividendHistory: List<DividendPeriod>,
    val totalDividendsBySymbol: List<SymbolDividend>,
    val kpis: PerformanceKpis
)

data class PeriodSummary(
    val period: String,
    val contributions: BigDecimal,
    val withdrawals: BigDecimal,
    val net: BigDecimal
)

data class ValuePoint(
    val date: String,
    val totalValue: BigDecimal,
    val costBasis: BigDecimal?
)

data class DividendPeriod(
    val period: String,
    val total: BigDecimal,
    val bySymbol: Map<String, BigDecimal>
)

data class SymbolDividend(
    val symbol: String,
    val total: BigDecimal
)

data class PerformanceKpis(
    val netContributions: BigDecimal,
    val monthlyAvgContributions: BigDecimal,
    val netChange: BigDecimal,
    val totalDividendIncome: BigDecimal,
    val avgMonthlyDividends: BigDecimal,
    val feesAndCommissions: BigDecimal
)

// ========== Activity Mapper ==========

fun BrokerActivity.toActivityDto() = BrokerActivityDto(
    id = id,
    type = type,
    symbol = symbol,
    description = description,
    quantity = quantity,
    price = price,
    amount = amount,
    fee = fee,
    currency = currency,
    tradeDate = tradeDate.toString(),
    settlementDate = settlementDate?.toString(),
    accountName = accountName,
    optionType = optionType
)
