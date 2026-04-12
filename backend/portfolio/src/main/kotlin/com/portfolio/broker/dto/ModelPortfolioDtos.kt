package com.portfolio.broker.dto

import com.portfolio.broker.entity.ModelPortfolio
import com.portfolio.broker.entity.ModelPortfolioAllocation
import java.math.BigDecimal

// ========== Request DTOs ==========

data class CreateModelPortfolioRequest(
    val name: String,
    val description: String? = null,
    val riskLevel: String,
    val allocations: List<ModelAllocationInput>
)

data class UpdateModelPortfolioRequest(
    val name: String? = null,
    val description: String? = null,
    val riskLevel: String? = null,
    val allocations: List<ModelAllocationInput>? = null
)

data class ModelAllocationInput(
    val symbol: String,
    val targetPercent: BigDecimal,
    val assetClass: String? = null
)

data class ApplyToAccountsRequest(
    val connectionIds: List<Long>
)

// ========== Response DTOs ==========

data class ModelPortfolioSummaryDto(
    val id: Long,
    val name: String,
    val description: String?,
    val riskLevel: String,
    val isSystem: Boolean,
    val allocationCount: Int,
    val totalPercent: BigDecimal
)

data class ModelPortfolioDetailDto(
    val id: Long,
    val name: String,
    val description: String?,
    val riskLevel: String,
    val isSystem: Boolean,
    val allocations: List<ModelAllocationDto>
)

data class ModelAllocationDto(
    val id: Long,
    val symbol: String,
    val targetPercent: BigDecimal,
    val assetClass: String?
)

data class ModelPortfoliosListResponse(
    val models: List<ModelPortfolioSummaryDto>
)

// ========== Rebalance Progress DTOs ==========

data class RebalanceProgressEntry(
    val symbol: String,
    val securityName: String?,
    val targetPercent: BigDecimal,
    val actualPercent: BigDecimal,
    val isNonModel: Boolean = false
)

data class RebalanceProgressDto(
    val connectionId: Long,
    val modelName: String,
    val accuracy: BigDecimal,
    val entries: List<RebalanceProgressEntry>
)

// ========== Pending Orders DTOs ==========

data class PendingOrderDto(
    val action: String,
    val symbol: String,
    val securityName: String?,
    val units: Int,
    val price: BigDecimal,
    val amount: BigDecimal,
    val currency: String,
    val accountName: String,
    val targetPercent: BigDecimal? = null,
    val targetValue: BigDecimal? = null,
    val cashInsufficient: Boolean = false
)

data class PendingOrdersResponse(
    val connectionId: Long,
    val orders: List<PendingOrderDto>,
    val totalAmount: BigDecimal,
    val cashRemaining: BigDecimal = BigDecimal.ZERO,
    val cashWarning: String? = null,
    val totalSellAmount: BigDecimal = BigDecimal.ZERO,
    val totalBuyAmount: BigDecimal = BigDecimal.ZERO
)

// ========== Model Analysis DTOs ==========

data class ExposureEntry(
    val name: String,
    val percentage: BigDecimal
)

data class ModelAnalysisDto(
    val modelId: Long,
    val sectorExposure: List<ExposureEntry>,
    val geographyExposure: List<ExposureEntry>,
    val riskScore: Int,
    val riskLevel: String,
    val holdings: List<ModelAllocationDto>
)

// ========== Mappers ==========

fun ModelPortfolio.toSummaryDto() = ModelPortfolioSummaryDto(
    id = id,
    name = name,
    description = description,
    riskLevel = riskLevel.name,
    isSystem = isSystem,
    allocationCount = allocations.size,
    totalPercent = allocations.sumOf { it.targetPercent }
)

fun ModelPortfolio.toDetailDto() = ModelPortfolioDetailDto(
    id = id,
    name = name,
    description = description,
    riskLevel = riskLevel.name,
    isSystem = isSystem,
    allocations = allocations.map { it.toDto() }
)

fun ModelPortfolioAllocation.toDto() = ModelAllocationDto(
    id = id,
    symbol = symbol,
    targetPercent = targetPercent,
    assetClass = assetClass
)
