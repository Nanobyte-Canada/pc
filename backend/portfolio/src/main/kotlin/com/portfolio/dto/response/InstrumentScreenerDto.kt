package com.portfolio.dto.response

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

data class InstrumentScreenerDto(
    val id: Long,
    val ticker: String,
    val name: String,
    val instrumentType: String,
    val isin: String?,
    val currency: String?,
    val country: String?,
    val exchange: String?,
    // Stock fields
    val sector: String?,
    val marketCap: Double?,
    val pe: Double?,
    val eps: Double?,
    val dividendYield: Double?,
    val weekHigh52: Double?,
    val weekLow52: Double?,
    val beta: Double?,
    // ETF fields
    val issuer: String?,
    val assetClass: String?,
    val expenseRatio: Double?,
    val yield: Double?,
    val totalAssets: Double?,
    val holdingsCount: Int?,
    val return1Y: Double?,
    // Mutual fund fields
    val fundCategory: String?,
    val fundStyle: String?,
    val nav: Double?
) {
    companion object {
        private val objectMapper = ObjectMapper()

        fun fromRow(row: Map<String, Any?>, instrumentType: String): InstrumentScreenerDto {
            val payload = parsePayload(row["raw_payload"])

            return InstrumentScreenerDto(
                id = (row["id"] as Number).toLong(),
                ticker = row["ticker"] as String,
                name = row["name"] as String,
                instrumentType = instrumentType,
                isin = row["isin"] as? String,
                currency = row["currency"] as? String,
                country = row["country"] as? String,
                exchange = payload?.path("General")?.path("Exchange")?.asTextOrNull(),
                // Stock fields
                sector = payload?.path("General")?.path("GicsSector")?.asTextOrNull(),
                marketCap = payload?.path("Highlights")?.path("MarketCapitalizationMln")?.asDoubleOrNull(),
                pe = payload?.path("Highlights")?.path("PERatio")?.asDoubleOrNull(),
                eps = payload?.path("Highlights")?.path("DilutedEpsTTM")?.asDoubleOrNull(),
                dividendYield = payload?.path("Highlights")?.path("DividendYield")?.asDoubleOrNull(),
                weekHigh52 = payload?.path("Technicals")?.path("52WeekHigh")?.asDoubleOrNull(),
                weekLow52 = payload?.path("Technicals")?.path("52WeekLow")?.asDoubleOrNull(),
                beta = payload?.path("Technicals")?.path("Beta")?.asDoubleOrNull(),
                // ETF fields
                issuer = payload?.path("ETF_Data")?.path("Company_Name")?.asTextOrNull(),
                assetClass = payload?.path("ETF_Data")?.path("Asset_Category")?.asTextOrNull(),
                expenseRatio = payload?.path("ETF_Data")?.path("NetExpenseRatio")?.asDoubleOrNull(),
                yield = payload?.path("ETF_Data")?.path("Yield")?.asDoubleOrNull()
                    ?: payload?.path("MutualFund_Data")?.path("Yield")?.asDoubleOrNull(),
                totalAssets = payload?.path("ETF_Data")?.path("TotalAssets")?.asDoubleOrNull(),
                holdingsCount = payload?.path("ETF_Data")?.path("Holdings_Count")?.asIntOrNull(),
                return1Y = payload?.path("ETF_Data")?.path("Performance")?.path("Returns_1Y")?.asDoubleOrNull()
                    ?: payload?.path("MutualFund_Data")?.path("Yield_1Year_YTD")?.asDoubleOrNull(),
                // Mutual fund fields
                fundCategory = payload?.path("MutualFund_Data")?.path("Fund_Category")?.asTextOrNull(),
                fundStyle = payload?.path("MutualFund_Data")?.path("Fund_Style")?.asTextOrNull(),
                nav = payload?.path("MutualFund_Data")?.path("Nav")?.asDoubleOrNull()
            )
        }

        fun parsePayload(value: Any?): JsonNode? {
            if (value == null) return null
            return try {
                when (value) {
                    is String -> objectMapper.readTree(value)
                    is JsonNode -> value
                    else -> objectMapper.readTree(value.toString())
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun JsonNode.asTextOrNull(): String? {
            if (isMissingNode || isNull) return null
            val text = asText()
            if (text.isNullOrBlank() || text == "null" || text == "None") return null
            return text
        }

        private fun JsonNode.asDoubleOrNull(): Double? {
            return asTextOrNull()?.toDoubleOrNull()
        }

        private fun JsonNode.asIntOrNull(): Int? {
            if (isNumber) return asInt()
            return asTextOrNull()?.toIntOrNull()
        }
    }
}
