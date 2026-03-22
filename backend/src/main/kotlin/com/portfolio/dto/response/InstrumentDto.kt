package com.portfolio.dto.response

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.entity.Etf
import com.portfolio.entity.Stock
import java.math.BigDecimal

enum class InstrumentType {
    STOCK, ETF
}

enum class MatchType {
    IDENTIFIER_EXACT,  // ISIN/CUSIP/SEDOL exact match
    TICKER_EXACT,      // Ticker/symbol exact match
    TICKER_PREFIX,     // Ticker/symbol starts with
    NAME_CONTAINS      // Name contains search term
}

data class SearchResultDto(
    val id: String,
    val type: InstrumentType,
    val ticker: String,
    val name: String,
    val exchange: String?,
    val matchType: MatchType,
    val status: String? = null,
    val isActive: Boolean? = null
)

data class SearchResponseDto(
    val data: List<SearchResultDto>,
    val meta: SearchMetaDto
)

data class SearchMetaDto(
    val query: String,
    val resultCount: Int,
    val searchTimeMs: Long
)

data class SectorDto(
    val code: String,
    val name: String
)

data class StockDto(
    val id: Long,
    val ticker: String,
    val exchange: String?,
    val name: String,
    val isin: String?,
    val cusip: String?,
    val sedol: String?,
    val currency: String,
    val country: String,
    val sector: SectorDto?,
    val status: String
) {
    companion object {
        fun from(stock: Stock) = StockDto(
            id = stock.id,
            ticker = stock.ticker,
            exchange = stock.exchangeCode,
            name = stock.name,
            isin = stock.isin,
            cusip = stock.cusip,
            sedol = stock.sedol,
            currency = stock.currency,
            country = stock.country,
            sector = null,
            status = stock.status.name
        )
    }
}

// ========== Stock Detail DTOs ==========

data class StockDetailDto(
    val id: Long,
    val ticker: String,
    val name: String,
    val currency: String,
    val country: String,
    val isin: String?,
    val avIngestionStatus: String,
    val sections: List<StockSectionDto>?
) {
    companion object {
        private val SKIP_KEYS = setOf("Symbol", "Name", "Exchange", "Currency", "Country", "Note", "Information")

        private val FIELD_GROUPS = listOf(
            StockFieldGroup("Company Overview", listOf(
                "Description" to "Description",
                "AssetType" to "Asset Type",
                "CIK" to "CIK",
                "Sector" to "Sector",
                "Industry" to "Industry",
                "Address" to "Address",
                "OfficialSite" to "Official Site",
                "FiscalYearEnd" to "Fiscal Year End",
                "LatestQuarter" to "Latest Quarter"
            )),
            StockFieldGroup("Market Data", listOf(
                "MarketCapitalization" to "Market Cap",
                "52WeekHigh" to "52-Week High",
                "52WeekLow" to "52-Week Low",
                "50DayMovingAverage" to "50-Day Moving Avg",
                "200DayMovingAverage" to "200-Day Moving Avg",
                "Beta" to "Beta",
                "BookValue" to "Book Value"
            )),
            StockFieldGroup("Valuation Ratios", listOf(
                "PERatio" to "P/E Ratio",
                "TrailingPE" to "Trailing P/E",
                "ForwardPE" to "Forward P/E",
                "PEGRatio" to "PEG Ratio",
                "PriceToSalesRatioTTM" to "Price/Sales (TTM)",
                "PriceToBookRatio" to "Price/Book",
                "EVToRevenue" to "EV/Revenue",
                "EVToEBITDA" to "EV/EBITDA"
            )),
            StockFieldGroup("Financials", listOf(
                "EPS" to "EPS",
                "DilutedEPSTTM" to "Diluted EPS (TTM)",
                "RevenuePerShareTTM" to "Revenue/Share (TTM)",
                "RevenueTTM" to "Revenue (TTM)",
                "GrossProfitTTM" to "Gross Profit (TTM)",
                "EBITDA" to "EBITDA",
                "ProfitMargin" to "Profit Margin",
                "OperatingMarginTTM" to "Operating Margin (TTM)",
                "ReturnOnAssetsTTM" to "Return on Assets (TTM)",
                "ReturnOnEquityTTM" to "Return on Equity (TTM)"
            )),
            StockFieldGroup("Growth", listOf(
                "QuarterlyEarningsGrowthYOY" to "Quarterly Earnings Growth (YoY)",
                "QuarterlyRevenueGrowthYOY" to "Quarterly Revenue Growth (YoY)"
            )),
            StockFieldGroup("Dividends", listOf(
                "DividendPerShare" to "Dividend/Share",
                "DividendYield" to "Dividend Yield",
                "DividendDate" to "Dividend Date",
                "ExDividendDate" to "Ex-Dividend Date"
            )),
            StockFieldGroup("Analyst Ratings", listOf(
                "AnalystTargetPrice" to "Target Price",
                "AnalystRatingStrongBuy" to "Strong Buy",
                "AnalystRatingBuy" to "Buy",
                "AnalystRatingHold" to "Hold",
                "AnalystRatingSell" to "Sell",
                "AnalystRatingStrongSell" to "Strong Sell"
            )),
            StockFieldGroup("Shares & Ownership", listOf(
                "SharesOutstanding" to "Shares Outstanding",
                "SharesFloat" to "Shares Float",
                "PercentInsiders" to "% Insiders",
                "PercentInstitutions" to "% Institutions"
            ))
        )

        private val LARGE_NUMBER_KEYS = setOf(
            "MarketCapitalization", "RevenueTTM", "GrossProfitTTM", "EBITDA"
        )
        private val SHARE_COUNT_KEYS = setOf("SharesOutstanding", "SharesFloat")
        private val GROUPED_KEYS = FIELD_GROUPS.flatMap { g -> g.fields.map { it.first } }.toSet()

        fun from(stock: Stock): StockDetailDto {
            val payload = stock.avRawPayload
            val sections = if (payload != null) buildSections(payload) else null
            return StockDetailDto(
                id = stock.id,
                ticker = stock.ticker,
                name = stock.name,
                currency = stock.currency,
                country = stock.country,
                isin = stock.isin,
                avIngestionStatus = stock.avIngestionStatus.name,
                sections = sections
            )
        }

        private fun buildSections(payload: JsonNode): List<StockSectionDto> {
            val sections = mutableListOf<StockSectionDto>()

            for (group in FIELD_GROUPS) {
                val fields = group.fields.mapNotNull { (key, label) ->
                    val raw = extractField(payload, key) ?: return@mapNotNull null
                    StockFieldDto(key = key, label = label, value = formatStockValue(key, raw))
                }
                if (fields.isNotEmpty()) {
                    sections.add(StockSectionDto(title = group.title, fields = fields))
                }
            }

            // "Other" section for ungrouped keys
            val otherFields = mutableListOf<StockFieldDto>()
            val fieldNames = payload.fieldNames()
            while (fieldNames.hasNext()) {
                val key = fieldNames.next()
                if (key in GROUPED_KEYS || key in SKIP_KEYS) continue
                val raw = extractField(payload, key) ?: continue
                otherFields.add(StockFieldDto(key = key, label = key, value = raw))
            }
            if (otherFields.isNotEmpty()) {
                sections.add(StockSectionDto(title = "Other", fields = otherFields))
            }

            return sections
        }

        private fun extractField(payload: JsonNode, key: String): String? {
            val node = payload.path(key)
            if (node.isMissingNode || node.isNull) return null
            val text = node.asText()
            if (text.isBlank() || text == "None" || text == "-" || text == "N/A") return null
            return text
        }

        private fun formatStockValue(key: String, raw: String): String {
            if (key == "Description") return raw
            val num = raw.toBigDecimalOrNull()
            if (num != null) {
                if (key in LARGE_NUMBER_KEYS) return formatCurrency(num)
                if (key in SHARE_COUNT_KEYS) return formatLargeNumber(num)
            }
            return raw
        }

        private fun formatCurrency(num: BigDecimal): String {
            val abs = num.abs()
            return when {
                abs >= BigDecimal("1000000000000") -> "$${num.divide(BigDecimal("1000000000000"), 2, java.math.RoundingMode.HALF_UP)}T"
                abs >= BigDecimal("1000000000") -> "$${num.divide(BigDecimal("1000000000"), 2, java.math.RoundingMode.HALF_UP)}B"
                abs >= BigDecimal("1000000") -> "$${num.divide(BigDecimal("1000000"), 2, java.math.RoundingMode.HALF_UP)}M"
                else -> "$$num"
            }
        }

        private fun formatLargeNumber(num: BigDecimal): String {
            val abs = num.abs()
            return when {
                abs >= BigDecimal("1000000000") -> "${num.divide(BigDecimal("1000000000"), 2, java.math.RoundingMode.HALF_UP)}B"
                abs >= BigDecimal("1000000") -> "${num.divide(BigDecimal("1000000"), 2, java.math.RoundingMode.HALF_UP)}M"
                else -> num.toPlainString()
            }
        }
    }
}

private data class StockFieldGroup(val title: String, val fields: List<Pair<String, String>>)

data class StockSectionDto(
    val title: String,
    val fields: List<StockFieldDto>
)

data class StockFieldDto(
    val key: String,
    val label: String,
    val value: String
)

// ========== ETF Detail DTOs ==========

data class EtfDetailDto(
    val id: Long,
    val symbol: String,
    val name: String,
    val issuer: String?,
    val assetClass: String?,
    val inceptionDate: String?,
    val enrichmentStatus: String,
    val summary: List<LabeledField>?,
    val description: String?,
    val portfolio: List<LabeledField>?,
    val performance: List<PerformancePeriodDto>?,
    val topHoldings: List<EtfTopHoldingDto>?,
    val holdingsCount: Int?,
    val sectorBreakdown: SectorBreakdownDto?
) {
    companion object {
        private val DESCRIPTION_FIELDS = setOf("description", "fundDescription", "investmentStrategy")

        private val PERIOD_LABELS = mapOf(
            "return1M" to "1 Month", "return3M" to "3 Months", "return6M" to "6 Months",
            "return1Y" to "1 Year", "return3Y" to "3 Years", "return5Y" to "5 Years",
            "return10Y" to "10 Years", "returnYTD" to "YTD"
        )
        private val PERIOD_ORDER = listOf(
            "return1M", "return3M", "return6M", "returnYTD",
            "return1Y", "return3Y", "return5Y", "return10Y"
        )

        fun from(etf: Etf): EtfDetailDto {
            val payload = etf.etfcomRawPayload
            val fundSummary = extractSection(payload, "fundSummaryData")
            val fundPortfolio = extractSection(payload, "fundPortfolioData")
            val perfData = extractSection(payload, "performanceData")
            val holdingsData = extractSection(payload, "topHoldings")
            val sectorData = extractSection(payload, "sectorIndustryBreakdown")

            return EtfDetailDto(
                id = etf.id,
                symbol = etf.symbol,
                name = etf.name,
                issuer = etf.issuer,
                assetClass = etf.assetClass,
                inceptionDate = etf.inceptionDate?.toString(),
                enrichmentStatus = etf.etfcomEnrichmentStatus.name,
                summary = parseLabeledFields(fundSummary),
                description = extractDescription(fundSummary),
                portfolio = parseLabeledFields(fundPortfolio),
                performance = parsePerformance(perfData),
                topHoldings = parseHoldings(holdingsData),
                holdingsCount = extractHoldingsCount(holdingsData),
                sectorBreakdown = parseSectorBreakdown(sectorData)
            )
        }

        private fun extractSection(payload: JsonNode?, key: String): JsonNode? {
            if (payload == null) return null
            val section = payload.path(key)
            if (section.isMissingNode || section.isNull) return null
            return section
        }

        // fundSummaryData and fundPortfolioData use: { fields: [{name, label, value}, ...] }
        private fun parseLabeledFields(node: JsonNode?): List<LabeledField>? {
            if (node == null) return null
            val fieldsArr = node.path("fields")
            if (!fieldsArr.isArray || fieldsArr.size() == 0) return null
            val result = mutableListOf<LabeledField>()
            for (item in fieldsArr) {
                val label = item.path("label").asText(null) ?: continue
                val value = item.path("value").asText(null) ?: continue
                if (value.isBlank() || value == "None" || value == "N/A" || value == "None%" || value == "null") continue
                result.add(LabeledField(label = label, value = value))
            }
            return result.ifEmpty { null }
        }

        private fun extractDescription(node: JsonNode?): String? {
            if (node == null) return null
            // Check fields array for description entries
            val fieldsArr = node.path("fields")
            if (fieldsArr.isArray) {
                for (item in fieldsArr) {
                    val name = item.path("name").asText("")
                    if (name in DESCRIPTION_FIELDS) {
                        val text = item.path("value").asText(null)
                        if (!text.isNullOrBlank() && text != "None" && text != "N/A") return text
                    }
                }
            }
            // Fallback: check direct fields
            for (key in DESCRIPTION_FIELDS) {
                val desc = node.path(key)
                if (!desc.isMissingNode && !desc.isNull) {
                    val text = desc.asText()
                    if (text.isNotBlank() && text != "None" && text != "N/A") return text
                }
            }
            return null
        }

        // performanceData: { data: [{ nav_data: {return1M, ...}, price_data: {return1M, ...} }] }
        private fun parsePerformance(node: JsonNode?): List<PerformancePeriodDto>? {
            if (node == null) return null
            val dataArr = node.path("data")
            if (!dataArr.isArray || dataArr.size() == 0) return null
            val entry = dataArr[0] ?: return null
            val navData = entry.path("nav_data")
            val priceData = entry.path("price_data")
            if ((navData.isMissingNode || navData.isNull) && (priceData.isMissingNode || priceData.isNull)) return null

            val periodKeys = mutableSetOf<String>()
            navData.fieldNames()?.let { while (it.hasNext()) periodKeys.add(it.next()) }
            priceData.fieldNames()?.let { while (it.hasNext()) periodKeys.add(it.next()) }

            val results = periodKeys
                .sortedBy { PERIOD_ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } }
                .mapNotNull { key ->
                    val nav = navData.path(key).let { if (it.isNumber) it.asDouble() else null }
                    val price = priceData.path(key).let { if (it.isNumber) it.asDouble() else null }
                    if (nav == null && price == null) return@mapNotNull null
                    PerformancePeriodDto(
                        period = PERIOD_LABELS[key] ?: key,
                        navReturn = nav,
                        priceReturn = price
                    )
                }
            return results.ifEmpty { null }
        }

        // topHoldings: { data: [ {name:"all_holdings", data:[{symbol, name, weight}, ...]}, ... ] }
        private fun parseHoldings(node: JsonNode?): List<EtfTopHoldingDto>? {
            if (node == null) return null
            val dataArr = node.path("data")
            if (!dataArr.isArray) return null

            // Find the "all_holdings" entry
            var holdingsArr: JsonNode? = null
            for (item in dataArr) {
                if (item.path("name").asText() == "all_holdings") {
                    holdingsArr = item.path("data")
                    break
                }
            }
            if (holdingsArr == null || !holdingsArr.isArray || holdingsArr.size() == 0) return null

            val holdings = mutableListOf<EtfTopHoldingDto>()
            for (item in holdingsArr) {
                if (!item.isObject) continue
                val symbol = item.path("symbol").asText(null)
                val name = item.path("name").asText(null) ?: "\u2014"
                val weight = parseWeight(item.path("weight").asText("0"))
                holdings.add(EtfTopHoldingDto(ticker = symbol ?: "\u2014", name = name, weight = weight))
            }
            return holdings.sortedByDescending { it.weight }.ifEmpty { null }
        }

        // topHoldings: { data: [ {name:"numberOfHoldings", value: 102}, ... ] }
        private fun extractHoldingsCount(node: JsonNode?): Int? {
            if (node == null) return null
            val dataArr = node.path("data")
            if (!dataArr.isArray) return null
            for (item in dataArr) {
                if (item.path("name").asText() == "numberOfHoldings") {
                    val v = item.path("value")
                    if (v.isInt) return v.asInt().takeIf { it > 0 }
                    return v.asText().toIntOrNull()?.takeIf { it > 0 }
                }
            }
            return null
        }

        // sectorIndustryBreakdown: { fields: [{name, weight}, ...] }
        private fun parseSectorBreakdown(node: JsonNode?): SectorBreakdownDto? {
            if (node == null) return null
            val fieldsArr = node.path("fields")
            if (!fieldsArr.isArray || fieldsArr.size() == 0) return null

            val sectors = mutableListOf<SectorEntryDto>()
            for (item in fieldsArr) {
                val name = item.path("name").asText(null) ?: continue
                val weight = parseWeight(item.path("weight").asText("0"))
                if (weight > 0) {
                    sectors.add(SectorEntryDto(name = name, weight = weight))
                }
            }
            return if (sectors.isNotEmpty()) {
                SectorBreakdownDto(
                    sectors = sectors.sortedByDescending { it.weight },
                    industries = emptyList()
                )
            } else null
        }

        private fun parseWeight(str: String): Double {
            val cleaned = str.replace("%", "").trim()
            val num = cleaned.toDoubleOrNull() ?: return 0.0
            return if (str.contains("%")) num / 100.0 else num
        }
    }
}

data class LabeledField(
    val label: String,
    val value: String
)

data class PerformancePeriodDto(
    val period: String,
    val navReturn: Double?,
    val priceReturn: Double?
)

data class EtfTopHoldingDto(
    val ticker: String,
    val name: String,
    val weight: Double
)

data class SectorBreakdownDto(
    val sectors: List<SectorEntryDto>,
    val industries: List<IndustryEntryDto>
)

data class SectorEntryDto(
    val name: String,
    val weight: Double
)

data class IndustryEntryDto(
    val name: String,
    val weight: Double,
    val parentSector: String?
)

// ========== ETF List DTO (unchanged) ==========

data class EtfDto(
    val id: Long,
    val symbol: String,
    val name: String,
    val isin: String?,
    val cusip: String?,
    val issuer: String?,
    val currency: String,
    val domicile: String,
    val inceptionDate: String?,
    val assetClass: String?,
    val status: String
) {
    companion object {
        fun from(etf: Etf): EtfDto {
            return EtfDto(
                id = etf.id,
                symbol = etf.symbol,
                name = etf.name,
                isin = etf.isin,
                cusip = etf.cusip,
                issuer = etf.issuer,
                currency = etf.currency,
                domicile = etf.domicile,
                inceptionDate = etf.inceptionDate?.toString(),
                assetClass = etf.assetClass,
                status = etf.status.name
            )
        }
    }
}
