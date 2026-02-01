package com.portfolio.broker.dto

import com.portfolio.broker.entity.*
import java.math.BigDecimal
import java.time.LocalTime
import java.time.OffsetDateTime

// Request DTOs
data class UpdateBrokerPrefsRequest(
    val autoFetchEnabled: Boolean,
    val fetchTimeUtc: String? = null // HH:mm format
)

// Response DTOs
data class BrokerDto(
    val id: Long,
    val code: String,
    val name: String,
    val authType: String,
    val status: String,
    val logoUrl: String?,
    val description: String?
)

data class BrokersResponse(
    val brokers: List<BrokerDto>
)

data class BrokerConnectionDto(
    val id: Long,
    val broker: BrokerDto,
    val accountNumber: String?,
    val accountType: String?,
    val accountName: String?,
    val status: String,
    val lastPositionsFetchedAt: OffsetDateTime?,
    val positionsCount: Int,
    val totalValue: BigDecimal?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime
)

data class BrokerConnectionsResponse(
    val connections: List<BrokerConnectionDto>
)

data class OAuthInitiateResponse(
    val redirectUrl: String,
    val state: String
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
    val currency: String
)

data class ConnectionPositionsResponse(
    val connectionId: Long,
    val broker: String,
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
    val broker: String,
    val accountNumber: String?,
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

data class BrokerPrefsDto(
    val autoFetchEnabled: Boolean,
    val fetchTimeUtc: String,
    val notificationOnFetch: Boolean,
    val notificationOnError: Boolean
)

data class BrokerPrefsResponse(
    val autoFetchEnabled: Boolean,
    val fetchTimeUtc: String,
    val message: String? = null
)

// Mappers
fun Broker.toDto() = BrokerDto(
    id = id,
    code = code,
    name = name,
    authType = authType.name,
    status = status.name,
    logoUrl = logoUrl,
    description = description
)

fun BrokerConnection.toDto() = BrokerConnectionDto(
    id = id,
    broker = broker.toDto(),
    accountNumber = accountNumber,
    accountType = accountType,
    accountName = accountName,
    status = status.name,
    lastPositionsFetchedAt = lastPositionsFetchedAt,
    positionsCount = positionsCount,
    totalValue = totalValue,
    errorMessage = connectionErrorMessage,
    createdAt = createdAt
)

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
    currency = currency
)

fun UserBrokerPrefs.toDto() = BrokerPrefsDto(
    autoFetchEnabled = autoFetchEnabled,
    fetchTimeUtc = fetchTimeUtc.toString(),
    notificationOnFetch = notificationOnFetch,
    notificationOnError = notificationOnError
)
