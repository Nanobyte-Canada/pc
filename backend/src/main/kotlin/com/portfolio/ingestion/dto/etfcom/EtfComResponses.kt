package com.portfolio.ingestion.dto.etfcom

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

// ========================================
// Fund Summary
// ========================================
@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComFundSummaryResponse(
    @JsonProperty("issuer") val issuer: String? = null,
    @JsonProperty("inceptionDate") val inceptionDate: String? = null,
    @JsonProperty("expenseRatio") val expenseRatio: BigDecimal? = null,
    @JsonProperty("aum") val aum: BigDecimal? = null,
    @JsonProperty("index") val indexTracked: String? = null,
    @JsonProperty("segment") val segment: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("fundName") val fundName: String? = null
)

// ========================================
// Top Holdings
// ========================================
@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComTopHoldingsResponse(
    @JsonProperty("holdings") val holdings: List<EtfComHolding>? = null,
    @JsonProperty("asOfDate") val asOfDate: String? = null,
    @JsonProperty("numberOfHoldings") val numberOfHoldings: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComHolding(
    @JsonProperty("symbol") val symbol: String? = null,
    @JsonProperty("holdingName") val holdingName: String? = null,
    @JsonProperty("weight") val weight: BigDecimal? = null,
    @JsonProperty("asOfDate") val asOfDate: String? = null
)

// ========================================
// Sector/Industry Breakdown
// ========================================
@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComSectorBreakdownResponse(
    @JsonProperty("sectors") val sectors: List<EtfComSector>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComSector(
    @JsonProperty("sectorName") val sectorName: String? = null,
    @JsonProperty("weight") val weight: BigDecimal? = null
)

// ========================================
// Performance Data
// ========================================
@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComPerformanceResponse(
    @JsonProperty("nav") val nav: EtfComPerformanceReturns? = null,
    @JsonProperty("price") val price: EtfComPerformanceReturns? = null,
    @JsonProperty("asOfDate") val asOfDate: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComPerformanceReturns(
    @JsonProperty("oneMonth") val oneMonth: BigDecimal? = null,
    @JsonProperty("threeMonth") val threeMonth: BigDecimal? = null,
    @JsonProperty("ytd") val ytd: BigDecimal? = null,
    @JsonProperty("oneYear") val oneYear: BigDecimal? = null,
    @JsonProperty("threeYear") val threeYear: BigDecimal? = null,
    @JsonProperty("fiveYear") val fiveYear: BigDecimal? = null
)

// ========================================
// Portfolio Data
// ========================================
@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComPortfolioDataResponse(
    @JsonProperty("weightedAvgMarketCap") val weightedAvgMarketCap: BigDecimal? = null,
    @JsonProperty("priceToEarnings") val priceToEarnings: BigDecimal? = null,
    @JsonProperty("priceToBook") val priceToBook: BigDecimal? = null,
    @JsonProperty("dividendYield") val dividendYield: BigDecimal? = null
)

// ========================================
// Combined enrichment data for a single ETF
// ========================================
data class EtfComEnrichmentData(
    val summary: EtfComFundSummaryResponse?,
    val holdings: EtfComTopHoldingsResponse?,
    val sectors: EtfComSectorBreakdownResponse?,
    val performance: EtfComPerformanceResponse?,
    val portfolio: EtfComPortfolioDataResponse?,
    val rawPayloads: Map<String, String>
)
