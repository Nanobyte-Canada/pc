package com.portfolio.ingestion.dto.eodhd

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response from EODHD Exchange Symbols List API
 * GET /api/exchange-symbol-list/{EXCHANGE}?api_token={API_TOKEN}&fmt=json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdInstrumentDto(
    @JsonProperty("Code")
    val code: String,

    @JsonProperty("Name")
    val name: String?,

    @JsonProperty("Country")
    val country: String?,

    @JsonProperty("Exchange")
    val exchange: String?,

    @JsonProperty("Currency")
    val currency: String?,

    @JsonProperty("Type")
    val type: String?,

    @JsonProperty("Isin")
    val isin: String?
) {
    fun isStock(): Boolean = type?.lowercase() in listOf("common stock", "stock")
    fun isEtf(): Boolean = type?.lowercase() == "etf"
    fun isMutualFund(): Boolean = type?.lowercase() in listOf("fund", "mutual fund")
}

/**
 * Response from EODHD Fundamentals API for ETF/Fund details
 * GET /api/fundamentals/{SYMBOL}.{EXCHANGE}?api_token={API_TOKEN}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdFundamentalsDto(
    @JsonProperty("General")
    val general: EodhdGeneralInfo?,

    @JsonProperty("ETF_Data")
    val etfData: EodhdEtfData?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdGeneralInfo(
    @JsonProperty("Code")
    val code: String?,

    @JsonProperty("Name")
    val name: String?,

    @JsonProperty("Exchange")
    val exchange: String?,

    @JsonProperty("CurrencyCode")
    val currencyCode: String?,

    @JsonProperty("CountryISO")
    val countryIso: String?,

    @JsonProperty("ISIN")
    val isin: String?,

    @JsonProperty("CUSIP")
    val cusip: String?,

    @JsonProperty("Sector")
    val sector: String?,

    @JsonProperty("Industry")
    val industry: String?,

    @JsonProperty("GicSector")
    val gicSector: String?,

    @JsonProperty("GicGroup")
    val gicGroup: String?,

    @JsonProperty("GicIndustry")
    val gicIndustry: String?,

    @JsonProperty("GicSubIndustry")
    val gicSubIndustry: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdEtfData(
    @JsonProperty("ISIN")
    val isin: String?,

    @JsonProperty("Company_Name")
    val companyName: String?,

    @JsonProperty("Company_URL")
    val companyUrl: String?,

    @JsonProperty("ETF_URL")
    val etfUrl: String?,

    @JsonProperty("Domicile")
    val domicile: String?,

    @JsonProperty("Index_Name")
    val indexName: String?,

    @JsonProperty("Yield")
    val yield: String?,

    @JsonProperty("Dividend_Paying_Frequency")
    val dividendFrequency: String?,

    @JsonProperty("Inception_Date")
    val inceptionDate: String?,

    @JsonProperty("Max_Annual_Mgmt_Charge")
    val maxAnnualMgmtCharge: String?,

    @JsonProperty("Ongoing_Charge")
    val ongoingCharge: String?,

    @JsonProperty("Date_Ongoing_Charge")
    val dateOngoingCharge: String?,

    @JsonProperty("NetExpenseRatio")
    val netExpenseRatio: String?,

    @JsonProperty("AnnualHoldingsTurnover")
    val annualHoldingsTurnover: String?,

    @JsonProperty("TotalAssets")
    val totalAssets: String?,

    @JsonProperty("Asset_Category")
    val assetCategory: String?,

    @JsonProperty("Holdings")
    val holdings: EodhdHoldings?
)

/**
 * Holdings data from EODHD Fundamentals API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdHoldings(
    @JsonProperty("Holdings_Count")
    val holdingsCount: Int?,

    @JsonProperty("Top_10_Holdings")
    val top10Holdings: List<EodhdHolding>?,

    @JsonProperty("Holdings")
    val allHoldings: Map<String, EodhdHolding>?
)

/**
 * Individual holding from EODHD
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdHolding(
    @JsonProperty("Code")
    val code: String?,

    @JsonProperty("Exchange")
    val exchange: String?,

    @JsonProperty("Name")
    val name: String?,

    @JsonProperty("Sector")
    val sector: String?,

    @JsonProperty("Industry")
    val industry: String?,

    @JsonProperty("Country")
    val country: String?,

    @JsonProperty("Assets_%")
    val assetsPercent: Double?
)
