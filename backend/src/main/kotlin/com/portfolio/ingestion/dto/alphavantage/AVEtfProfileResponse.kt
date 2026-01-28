package com.portfolio.ingestion.dto.alphavantage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/**
 * DTO for Alpha Vantage ETF_PROFILE endpoint response.
 * API: GET https://www.alphavantage.co/query?function=ETF_PROFILE&symbol={symbol}&apikey={apikey}
 *
 * Note: Alpha Vantage returns values as strings. Sector allocations are decimals (0.519 = 51.9%).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AVEtfProfileResponse(
    @JsonProperty("symbol")
    val symbol: String? = null,

    @JsonProperty("name")
    val name: String? = null,

    @JsonProperty("asset_type")
    val assetType: String? = null,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("net_assets")
    val netAssets: String? = null,

    @JsonProperty("net_expense_ratio")
    val netExpenseRatio: String? = null,

    @JsonProperty("portfolio_turnover")
    val portfolioTurnover: String? = null,

    @JsonProperty("dividend_yield")
    val dividendYield: String? = null,

    @JsonProperty("inception_date")
    val inceptionDate: String? = null,

    @JsonProperty("leveraged")
    val leveraged: String? = null,

    @JsonProperty("holdings_count")
    val holdingsCount: String? = null,

    @JsonProperty("sectors")
    val sectors: List<AVSector>? = null,

    @JsonProperty("holdings")
    val holdings: List<AVHolding>? = null,

    // Error/rate limit fields
    @JsonProperty("Note")
    val note: String? = null,

    @JsonProperty("Information")
    val information: String? = null
) {
    /**
     * Check if this is a valid response.
     * Note: Alpha Vantage ETF_PROFILE does NOT return symbol, name, asset_type, or description.
     * We check for presence of data fields that ARE returned by the API.
     *
     * Important: Alpha Vantage sometimes returns both data AND a Note/Information field
     * (e.g., "Thank you for using Alpha Vantage!" or API limit warnings). We only reject
     * responses that are rate-limited, not those with informational messages.
     */
    fun isValid(): Boolean = hasData() && !isRateLimited()

    /**
     * Check if the response contains actual ETF data.
     * Alpha Vantage returns {} (empty object) when ETF is not found.
     * If ANY data field is present, this is a valid response.
     */
    private fun hasData(): Boolean = !isEmptyResponse()

    /**
     * Check if this response represents an empty {} from Alpha Vantage.
     * All data fields will be null when deserializing {}.
     */
    private fun isEmptyResponse(): Boolean =
        netAssets == null &&
        netExpenseRatio == null &&
        portfolioTurnover == null &&
        dividendYield == null &&
        inceptionDate == null &&
        leveraged == null &&
        holdingsCount == null &&
        sectors.isNullOrEmpty() &&
        holdings.isNullOrEmpty()

    /**
     * Check if the response contains an error or rate limit message.
     */
    private fun hasError(): Boolean = note != null || information != null

    /**
     * Check if we hit the rate limit
     */
    fun isRateLimited(): Boolean = note?.contains("API call frequency") == true ||
            information?.contains("API call frequency") == true

    /**
     * Parse leveraged field to boolean
     */
    fun isLeveraged(): Boolean = leveraged?.uppercase() == "YES" || leveraged?.uppercase() == "TRUE"

    /**
     * Safe conversion of string to BigDecimal
     */
    fun parseDecimal(value: String?): BigDecimal? {
        if (value.isNullOrBlank() || value == "None" || value == "-" || value == "N/A") {
            return null
        }
        return try {
            BigDecimal(value)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Safe conversion of string to Int
     */
    fun parseInt(value: String?): Int? {
        if (value.isNullOrBlank() || value == "None" || value == "-" || value == "N/A") {
            return null
        }
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Get sector allocation by GICS sector name.
     * Common sector names from Alpha Vantage:
     * - Information Technology
     * - Communication Services
     * - Consumer Discretionary
     * - Consumer Staples
     * - Health Care
     * - Industrials
     * - Utilities
     * - Materials
     * - Energy
     * - Financials
     * - Real Estate
     */
    fun getSectorAllocation(sectorName: String): BigDecimal? {
        val sectorItem = sectors?.find {
            it.sector.equals(sectorName, ignoreCase = true)
        }
        return parseDecimal(sectorItem?.weight)
    }
}

/**
 * Individual sector allocation within an ETF.
 * Alpha Vantage returns sectors as an array of objects with "sector" and "weight" fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AVSector(
    @JsonProperty("sector")
    val sector: String? = null,

    @JsonProperty("weight")
    val weight: String? = null
)

/**
 * Individual holding within an ETF
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AVHolding(
    @JsonProperty("symbol")
    val symbol: String? = null,

    @JsonProperty("description")
    val description: String? = null,

    @JsonProperty("name")
    val name: String? = null,

    @JsonProperty("weight")
    val weight: String? = null
) {
    fun parseWeight(): BigDecimal? {
        if (weight.isNullOrBlank() || weight == "None" || weight == "-") {
            return null
        }
        return try {
            BigDecimal(weight)
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/**
 * Mapped sector allocations for easy access to all 11 GICS sectors
 */
data class SectorAllocations(
    val informationTechnology: BigDecimal? = null,
    val communicationServices: BigDecimal? = null,
    val consumerDiscretionary: BigDecimal? = null,
    val consumerStaples: BigDecimal? = null,
    val healthCare: BigDecimal? = null,
    val industrials: BigDecimal? = null,
    val utilities: BigDecimal? = null,
    val materials: BigDecimal? = null,
    val energy: BigDecimal? = null,
    val financials: BigDecimal? = null,
    val realEstate: BigDecimal? = null
) {
    companion object {
        fun fromResponse(response: AVEtfProfileResponse): SectorAllocations {
            return SectorAllocations(
                informationTechnology = response.getSectorAllocation("Information Technology"),
                communicationServices = response.getSectorAllocation("Communication Services"),
                consumerDiscretionary = response.getSectorAllocation("Consumer Discretionary"),
                consumerStaples = response.getSectorAllocation("Consumer Staples"),
                healthCare = response.getSectorAllocation("Health Care"),
                industrials = response.getSectorAllocation("Industrials"),
                utilities = response.getSectorAllocation("Utilities"),
                materials = response.getSectorAllocation("Materials"),
                energy = response.getSectorAllocation("Energy"),
                financials = response.getSectorAllocation("Financials"),
                realEstate = response.getSectorAllocation("Real Estate")
            )
        }
    }
}
