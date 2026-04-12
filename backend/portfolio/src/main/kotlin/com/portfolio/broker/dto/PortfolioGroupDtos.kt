package com.portfolio.broker.dto

import com.portfolio.broker.entity.*
import java.math.BigDecimal

// ========== Request DTOs ==========

data class CreatePortfolioGroupRequest(
    val name: String,
    val description: String? = null,
    val targets: List<TargetInput>? = null,
    val accountIds: List<Long>? = null
)

data class UpdatePortfolioGroupRequest(
    val name: String? = null,
    val description: String? = null
)

data class SetTargetsRequest(
    val targets: List<TargetInput>
)

data class TargetInput(
    val symbol: String,
    val targetPercent: BigDecimal
)

data class LinkAccountRequest(
    val connectionId: Long
)

data class UpdateSettingsRequest(
    val sellToRebalance: Boolean? = null,
    val keepCurrenciesSeparate: Boolean? = null,
    val preventNonTradableTrades: Boolean? = null,
    val notifyNewAssets: Boolean? = null,
    val retainCashForExchange: Boolean? = null,
    val rebalanceFrequency: String? = null,
    val accuracyThreshold: BigDecimal? = null,
    val autoExecute: Boolean? = null
)

data class ExcludeAssetRequest(
    val symbol: String
)

// ========== Response DTOs ==========

data class PortfolioGroupSummaryDto(
    val id: Long,
    val name: String,
    val description: String?,
    val accountCount: Int,
    val targetCount: Int,
    val totalValue: BigDecimal,
    val accuracy: BigDecimal
)

data class PortfolioGroupDetailDto(
    val id: Long,
    val name: String,
    val description: String?,
    val targets: List<TargetAllocationDto>,
    val linkedAccounts: List<LinkedAccountDto>,
    val settings: PortfolioGroupSettingsDto,
    val excludedAssets: List<ExcludedAssetDto>,
    val totalValue: BigDecimal,
    val accuracy: BigDecimal
)

data class TargetAllocationDto(
    val id: Long,
    val symbol: String,
    val targetPercent: BigDecimal
)

data class LinkedAccountDto(
    val connectionId: Long,
    val accountName: String?,
    val accountNumber: String?,
    val accountType: String?,
    val totalValue: BigDecimal?,
    val status: String
)

data class PortfolioGroupSettingsDto(
    val sellToRebalance: Boolean,
    val keepCurrenciesSeparate: Boolean,
    val preventNonTradableTrades: Boolean,
    val notifyNewAssets: Boolean,
    val retainCashForExchange: Boolean,
    val rebalanceFrequency: String,
    val accuracyThreshold: BigDecimal,
    val autoExecute: Boolean,
    val lastRebalancedAt: java.time.OffsetDateTime? = null,
    val nextRebalanceDate: java.time.LocalDate? = null
)

data class DriftHoldingDto(
    val symbol: String,
    val securityName: String?,
    val targetPercent: BigDecimal,
    val actualPercent: BigDecimal,
    val driftPercent: BigDecimal,
    val actualValue: BigDecimal,
    val targetValue: BigDecimal,
    val currency: String
)

data class ExcludedAssetDto(
    val symbol: String,
    val securityName: String?,
    val currentValue: BigDecimal?,
    val currency: String?
)

data class NewAssetDto(
    val symbol: String,
    val securityName: String?,
    val currentValue: BigDecimal?,
    val currency: String?
)

data class DriftAnalysisResponse(
    val groupId: Long,
    val groupName: String,
    val accuracy: BigDecimal,
    val totalValue: BigDecimal,
    val cash: Map<String, BigDecimal>,
    val holdings: List<DriftHoldingDto>,
    val excludedAssets: List<ExcludedAssetDto>,
    val newAssets: List<NewAssetDto>
)

data class RebalanceTradeDto(
    val action: String, // BUY or SELL
    val symbol: String,
    val securityName: String?,
    val units: BigDecimal,
    val price: BigDecimal,
    val amount: BigDecimal,
    val currency: String,
    val accountName: String?,
    val connectionId: Long
)

data class RebalanceTradesResponse(
    val groupId: Long,
    val trades: List<RebalanceTradeDto>,
    val cashRemaining: Map<String, BigDecimal>,
    val resultingAccuracy: BigDecimal
)

data class PortfolioGroupsListResponse(
    val groups: List<PortfolioGroupSummaryDto>
)

// ========== Mappers ==========

fun PortfolioTarget.toDto() = TargetAllocationDto(
    id = id,
    symbol = symbol,
    targetPercent = targetPercent
)

fun PortfolioGroupAccount.toLinkedAccountDto() = LinkedAccountDto(
    connectionId = connection.id,
    accountName = connection.accountName,
    accountNumber = connection.accountNumber,
    accountType = connection.accountType,
    totalValue = connection.totalValue,
    status = connection.status.name
)

fun PortfolioGroupSettings.toDto() = PortfolioGroupSettingsDto(
    sellToRebalance = sellToRebalance,
    keepCurrenciesSeparate = keepCurrenciesSeparate,
    preventNonTradableTrades = preventNonTradableTrades,
    notifyNewAssets = notifyNewAssets,
    retainCashForExchange = retainCashForExchange,
    rebalanceFrequency = rebalanceFrequency.name,
    accuracyThreshold = accuracyThreshold,
    autoExecute = autoExecute,
    lastRebalancedAt = lastRebalancedAt,
    nextRebalanceDate = nextRebalanceDate
)

fun PortfolioExcludedAsset.toDto(currentValue: BigDecimal? = null, currency: String? = null) = ExcludedAssetDto(
    symbol = symbol,
    securityName = null,
    currentValue = currentValue,
    currency = currency
)

val DEFAULT_SETTINGS_DTO = PortfolioGroupSettingsDto(
    sellToRebalance = false,
    keepCurrenciesSeparate = false,
    preventNonTradableTrades = false,
    notifyNewAssets = true,
    retainCashForExchange = false,
    rebalanceFrequency = "MANUAL",
    accuracyThreshold = BigDecimal("90.00"),
    autoExecute = false
)

// ========== Rebalance Event ==========

data class RebalanceEventDto(
    val id: Long,
    val groupId: Long,
    val triggerType: String,
    val accuracyBefore: BigDecimal?,
    val accuracyAfter: BigDecimal?,
    val tradesCount: Int,
    val batchId: String?,
    val status: String,
    val notes: String?,
    val createdAt: java.time.OffsetDateTime
)

data class RebalanceHistoryResponse(
    val events: List<RebalanceEventDto>
)

fun com.portfolio.broker.entity.RebalanceEvent.toDto() = RebalanceEventDto(
    id = id,
    groupId = group.id,
    triggerType = triggerType.name,
    accuracyBefore = accuracyBefore,
    accuracyAfter = accuracyAfter,
    tradesCount = tradesCount,
    batchId = batchId?.toString(),
    status = status.name,
    notes = notes,
    createdAt = createdAt
)
