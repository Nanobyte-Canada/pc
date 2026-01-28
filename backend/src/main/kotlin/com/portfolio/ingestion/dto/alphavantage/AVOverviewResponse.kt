package com.portfolio.ingestion.dto.alphavantage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/**
 * DTO for Alpha Vantage OVERVIEW endpoint response.
 * API: GET https://www.alphavantage.co/query?function=OVERVIEW&symbol={symbol}&apikey={apikey}
 *
 * Note: Alpha Vantage returns all values as strings, including numbers.
 * Nulls may be represented as "None" or "0" for some fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AVOverviewResponse(
    @JsonProperty("Symbol")
    val symbol: String? = null,

    @JsonProperty("AssetType")
    val assetType: String? = null,

    @JsonProperty("Name")
    val name: String? = null,

    @JsonProperty("Description")
    val description: String? = null,

    @JsonProperty("CIK")
    val cik: String? = null,

    @JsonProperty("Exchange")
    val exchange: String? = null,

    @JsonProperty("Currency")
    val currency: String? = null,

    @JsonProperty("Country")
    val country: String? = null,

    @JsonProperty("Sector")
    val sector: String? = null,

    @JsonProperty("Industry")
    val industry: String? = null,

    @JsonProperty("Address")
    val address: String? = null,

    @JsonProperty("OfficialSite")
    val officialSite: String? = null,

    @JsonProperty("FiscalYearEnd")
    val fiscalYearEnd: String? = null,

    @JsonProperty("LatestQuarter")
    val latestQuarter: String? = null,

    @JsonProperty("MarketCapitalization")
    val marketCapitalization: String? = null,

    @JsonProperty("EBITDA")
    val ebitda: String? = null,

    @JsonProperty("PERatio")
    val peRatio: String? = null,

    @JsonProperty("PEGRatio")
    val pegRatio: String? = null,

    @JsonProperty("BookValue")
    val bookValue: String? = null,

    @JsonProperty("DividendPerShare")
    val dividendPerShare: String? = null,

    @JsonProperty("DividendYield")
    val dividendYield: String? = null,

    @JsonProperty("EPS")
    val eps: String? = null,

    @JsonProperty("RevenuePerShareTTM")
    val revenuePerShareTTM: String? = null,

    @JsonProperty("ProfitMargin")
    val profitMargin: String? = null,

    @JsonProperty("OperatingMarginTTM")
    val operatingMarginTTM: String? = null,

    @JsonProperty("ReturnOnAssetsTTM")
    val returnOnAssetsTTM: String? = null,

    @JsonProperty("ReturnOnEquityTTM")
    val returnOnEquityTTM: String? = null,

    @JsonProperty("RevenueTTM")
    val revenueTTM: String? = null,

    @JsonProperty("GrossProfitTTM")
    val grossProfitTTM: String? = null,

    @JsonProperty("QuarterlyEarningsGrowthYOY")
    val quarterlyEarningsGrowthYOY: String? = null,

    @JsonProperty("QuarterlyRevenueGrowthYOY")
    val quarterlyRevenueGrowthYOY: String? = null,

    @JsonProperty("AnalystTargetPrice")
    val analystTargetPrice: String? = null,

    @JsonProperty("AnalystRatingStrongBuy")
    val analystRatingStrongBuy: String? = null,

    @JsonProperty("AnalystRatingBuy")
    val analystRatingBuy: String? = null,

    @JsonProperty("AnalystRatingHold")
    val analystRatingHold: String? = null,

    @JsonProperty("AnalystRatingSell")
    val analystRatingSell: String? = null,

    @JsonProperty("AnalystRatingStrongSell")
    val analystRatingStrongSell: String? = null,

    @JsonProperty("TrailingPE")
    val trailingPE: String? = null,

    @JsonProperty("ForwardPE")
    val forwardPE: String? = null,

    @JsonProperty("52WeekHigh")
    val week52High: String? = null,

    @JsonProperty("52WeekLow")
    val week52Low: String? = null,

    @JsonProperty("50DayMovingAverage")
    val day50MovingAverage: String? = null,

    @JsonProperty("200DayMovingAverage")
    val day200MovingAverage: String? = null,

    @JsonProperty("SharesOutstanding")
    val sharesOutstanding: String? = null,

    @JsonProperty("Beta")
    val beta: String? = null,

    @JsonProperty("DividendDate")
    val dividendDate: String? = null,

    @JsonProperty("ExDividendDate")
    val exDividendDate: String? = null,

    // Valuation ratios
    @JsonProperty("PriceToSalesRatioTTM")
    val priceToSalesRatioTTM: String? = null,

    @JsonProperty("PriceToBookRatio")
    val priceToBookRatio: String? = null,

    @JsonProperty("EVToRevenue")
    val evToRevenue: String? = null,

    @JsonProperty("EVToEBITDA")
    val evToEbitda: String? = null,

    @JsonProperty("DilutedEPSTTM")
    val dilutedEpsTTM: String? = null,

    // Shares & Ownership
    @JsonProperty("SharesFloat")
    val sharesFloat: String? = null,

    @JsonProperty("PercentInsiders")
    val percentInsiders: String? = null,

    @JsonProperty("PercentInstitutions")
    val percentInstitutions: String? = null,

    // Error/rate limit fields
    @JsonProperty("Note")
    val note: String? = null,

    @JsonProperty("Information")
    val information: String? = null
) {
    /**
     * Check if this is a valid response (not an error or rate limit message)
     */
    fun isValid(): Boolean = symbol != null && note == null && information == null

    /**
     * Check if we hit the rate limit
     */
    fun isRateLimited(): Boolean = note?.contains("API call frequency") == true ||
            information?.contains("API call frequency") == true

    /**
     * Safe conversion of string to BigDecimal, handling "None", "-", empty strings
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
     * Safe conversion of string to Long
     */
    fun parseLong(value: String?): Long? {
        if (value.isNullOrBlank() || value == "None" || value == "-" || value == "N/A") {
            return null
        }
        return try {
            value.toLong()
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
}
