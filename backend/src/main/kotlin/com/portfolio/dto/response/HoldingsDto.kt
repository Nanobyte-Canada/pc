package com.portfolio.dto.response

import com.portfolio.entity.EtfHolding
import com.portfolio.entity.HoldingDataSource
import com.portfolio.entity.ResolutionStatus
import java.time.LocalDate

data class HoldingDto(
    val stockId: Long?,
    val ticker: String,
    val name: String,
    val weight: Double?,
    val shares: Double?,
    val marketValue: Double?,
    val sector: SectorDto?,
    val country: String?,
    val isResolved: Boolean = true,
    val rawTicker: String? = null,
    val rawName: String? = null,
    val dataSource: String? = null,
    val holdingType: String? = null,
    val rank: Int? = null,
    val resolutionStatus: String? = null
)

data class EtfHoldingsResponseDto(
    val etfId: Long,
    val etfSymbol: String,
    val asOfDate: LocalDate,
    val holdingsCount: Int,
    val holdings: List<HoldingDto>,
    val metadata: HoldingsMetadataDto? = null
)

data class HoldingsMetadataDto(
    val resolvedCount: Int,
    val unresolvedCount: Int,
    val resolvedPercent: Double,
    val primaryDataSource: String?,
    val hasEnrichmentData: Boolean
)

data class AvailableDatesResponseDto(
    val dates: List<LocalDate>
)

fun EtfHolding.toHoldingDto(): HoldingDto {
    val resolvedStock = stock
    val displayTicker = resolvedStock?.ticker ?: rawTicker ?: "UNKNOWN"
    val displayName = resolvedStock?.name ?: rawName ?: "Unknown Holding"

    return HoldingDto(
        stockId = resolvedStock?.id,
        ticker = displayTicker,
        name = displayName,
        weight = (etfcomWeight ?: avWeight ?: weight)?.toDouble(),
        shares = shares?.toDouble(),
        marketValue = marketValue?.toDouble(),
        sector = null,
        country = resolvedStock?.country,
        isResolved = resolvedStock != null,
        rawTicker = rawTicker,
        rawName = rawName,
        dataSource = dataSource.name,
        holdingType = holdingType.name,
        rank = rank,
        resolutionStatus = resolutionStatus.name
    )
}

fun List<EtfHolding>.toMetadata(): HoldingsMetadataDto {
    val resolved = count { it.resolutionStatus == ResolutionStatus.RESOLVED }
    val unresolved = count { it.resolutionStatus != ResolutionStatus.RESOLVED }
    val total = size
    val resolvedPercent = if (total > 0) (resolved.toDouble() / total * 100) else 0.0

    val sourceCounts = groupBy { it.dataSource }
    val primarySource = sourceCounts.maxByOrNull { it.value.size }?.key

    val hasEnrichment = any {
        it.dataSource == HoldingDataSource.ALPHA_VANTAGE || it.dataSource == HoldingDataSource.ETF_COM
    }

    return HoldingsMetadataDto(
        resolvedCount = resolved,
        unresolvedCount = unresolved,
        resolvedPercent = resolvedPercent,
        primaryDataSource = primarySource?.name,
        hasEnrichmentData = hasEnrichment
    )
}
