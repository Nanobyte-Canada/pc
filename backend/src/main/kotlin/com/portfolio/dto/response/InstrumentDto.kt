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
        private val SKIP_FIELDS = setOf("__typename", "fundName", "ticker", "symbol", "name", "label", "fundTicker")
        private val DESCRIPTION_FIELDS = setOf("description", "fundDescription", "investmentStrategy")
        private val LARGE_FIELDS = setOf(
            "expenseRatio", "aum", "peRatio", "weightedAvgMarketCap",
            "netExpenseRatio", "totalAssets", "assetsUnderManagement"
        )
        private val FIELD_LABELS = mapOf(
            "issuer" to "Issuer",
            "inceptionDate" to "Inception Date",
            "expenseRatio" to "Expense Ratio",
            "netExpenseRatio" to "Net Expense Ratio",
            "aum" to "AUM",
            "totalAssets" to "Total Assets",
            "assetsUnderManagement" to "AUM",
            "indexTracked" to "Index Tracked",
            "segment" to "Segment",
            "assetClass" to "Asset Class",
            "legalStructure" to "Legal Structure",
            "fundFamily" to "Fund Family",
            "weightedAvgMarketCap" to "Wtd Avg Market Cap",
            "peRatio" to "P/E Ratio",
            "pbRatio" to "P/B Ratio",
            "dividendYield" to "Dividend Yield",
            "numberOfHoldings" to "Number of Holdings",
            "avgDailyVolume" to "Avg Daily Volume",
            "avgDailyDollarVolume" to "Avg Daily $ Volume",
            "spread" to "Spread"
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
                holdingsCount = extractHoldingsCount(fundSummary),
                sectorBreakdown = parseSectorBreakdown(sectorData)
            )
        }

        private fun extractSection(payload: JsonNode?, key: String): JsonNode? {
            if (payload == null) return null
            val section = payload.path(key)
            if (section.isMissingNode || section.isNull) return null
            // Old format: section has data wrapper
            val nested = section.path("data").path(key)
            if (!nested.isMissingNode && !nested.isNull) return nested
            return section
        }

        private fun parseLabeledFields(node: JsonNode?): List<LabeledField>? {
            if (node == null || !node.isObject) return null
            val fields = mutableListOf<LabeledField>()
            val iter = node.fields()
            while (iter.hasNext()) {
                val (key, value) = iter.next()
                if (key in SKIP_FIELDS || key in DESCRIPTION_FIELDS) continue
                if (value.isNull || value.isMissingNode) continue
                if (value.isObject || value.isArray) continue
                val text = value.asText()
                if (text.isBlank() || text == "N/A" || text == "None") continue
                fields.add(LabeledField(
                    label = FIELD_LABELS[key] ?: formatCamelCase(key),
                    value = formatEtfFieldValue(key, text)
                ))
            }
            return fields.ifEmpty { null }
        }

        private fun extractDescription(node: JsonNode?): String? {
            if (node == null) return null
            for (key in DESCRIPTION_FIELDS) {
                val desc = node.path(key)
                if (!desc.isMissingNode && !desc.isNull) {
                    val text = desc.asText()
                    if (text.isNotBlank() && text != "None" && text != "N/A") return text
                }
            }
            return null
        }

        private fun parsePerformance(node: JsonNode?): List<PerformancePeriodDto>? {
            if (node == null || !node.isObject) return null
            val periodMap = mutableMapOf<String, Pair<Double?, Double?>>()

            val iter = node.fields()
            while (iter.hasNext()) {
                val (key, value) = iter.next()
                if (key in SKIP_FIELDS || value.isObject || value.isArray) continue
                val num = value.asDouble(Double.NaN)
                if (num.isNaN()) continue

                val lower = key.lowercase()
                val type = when {
                    lower.contains("nav") -> "nav"
                    lower.contains("price") || lower.contains("mkt") -> "price"
                    else -> continue
                }

                val period = key
                    .replace(Regex("(?i)nav|price|mkt|return"), "")
                    .replace(Regex("([A-Z])"), " $1")
                    .trim()
                    .ifEmpty { key }

                val existing = periodMap[period] ?: Pair(null, null)
                periodMap[period] = if (type == "nav") {
                    existing.copy(first = num)
                } else {
                    existing.copy(second = num)
                }
            }

            if (periodMap.isEmpty()) return null

            return periodMap.map { (period, values) ->
                PerformancePeriodDto(
                    period = period.trimStart(),
                    navReturn = values.first,
                    priceReturn = values.second
                )
            }
        }

        private fun parseHoldings(node: JsonNode?): List<EtfTopHoldingDto>? {
            if (node == null) return null
            // If it's an array directly
            val arr = if (node.isArray) node else {
                // Try to find an array inside the object
                val iter = node.fields()
                var found: JsonNode? = null
                while (iter.hasNext()) {
                    val (_, v) = iter.next()
                    if (v.isArray && v.size() > 0) { found = v; break }
                }
                found
            }
            if (arr == null || !arr.isArray || arr.size() == 0) return null

            val holdings = mutableListOf<EtfTopHoldingDto>()
            for (item in arr) {
                if (!item.isObject) continue
                val ticker = item.path("ticker").asText(null)
                    ?: item.path("symbol").asText(null)
                    ?: item.path("holding").asText(null)
                    ?: "\u2014"
                val name = item.path("name").asText(null)
                    ?: item.path("holdingName").asText(null)
                    ?: item.path("description").asText(null)
                    ?: "\u2014"
                val weight = parseWeight(
                    item.path("weighting").asText(null)
                        ?: item.path("weight").asText(null)
                        ?: item.path("percentOfFund").asText(null)
                        ?: "0"
                )
                holdings.add(EtfTopHoldingDto(ticker = ticker, name = name, weight = weight))
            }
            return holdings.sortedByDescending { it.weight }.ifEmpty { null }
        }

        private fun extractHoldingsCount(summaryNode: JsonNode?): Int? {
            if (summaryNode == null) return null
            val node = summaryNode.path("numberOfHoldings")
            if (node.isMissingNode || node.isNull) return null
            return node.asInt(0).takeIf { it > 0 }
        }

        private fun parseSectorBreakdown(node: JsonNode?): SectorBreakdownDto? {
            if (node == null) return null

            val sectors = mutableListOf<SectorEntryDto>()
            val industries = mutableListOf<IndustryEntryDto>()

            if (node.isArray) {
                // Array of sector objects
                for (item in node) {
                    if (!item.isObject) continue
                    val name = item.path("sectorName").asText(null)
                        ?: item.path("name").asText(null)
                        ?: item.path("sector").asText(null)
                        ?: continue
                    val weight = parseWeight(
                        item.path("weight").asText(null)
                            ?: item.path("sectorWeight").asText(null)
                            ?: item.path("percentage").asText(null)
                            ?: "0"
                    )
                    sectors.add(SectorEntryDto(name = name, weight = weight))

                    // Check for nested industries
                    val nested = when {
                        item.has("industries") -> item.path("industries")
                        item.has("industryGroups") -> item.path("industryGroups")
                        item.has("subSectors") -> item.path("subSectors")
                        else -> null
                    }
                    if (nested != null && nested.isArray) {
                        for (ind in nested) {
                            val indName = ind.path("name").asText(null)
                                ?: ind.path("industryName").asText(null)
                                ?: continue
                            val indWeight = parseWeight(
                                ind.path("weight").asText(null)
                                    ?: ind.path("percentage").asText(null)
                                    ?: "0"
                            )
                            industries.add(IndustryEntryDto(name = indName, weight = indWeight, parentSector = name))
                        }
                    }
                }
            } else if (node.isObject) {
                // Try to find sector/industry arrays
                val sectorArr = findArray(node, "sectors", "sectorBreakdown", "sectorWeights")
                val industryArr = findArray(node, "industries", "industryBreakdown", "industryWeights")

                if (sectorArr != null) {
                    for (item in sectorArr) {
                        if (!item.isObject) continue
                        val name = item.path("sectorName").asText(null)
                            ?: item.path("name").asText(null)
                            ?: item.path("sector").asText(null)
                            ?: continue
                        val weight = parseWeight(
                            item.path("weight").asText(null)
                                ?: item.path("sectorWeight").asText(null)
                                ?: item.path("percentage").asText(null)
                                ?: "0"
                        )
                        sectors.add(SectorEntryDto(name = name, weight = weight))
                    }
                }

                if (industryArr != null) {
                    for (item in industryArr) {
                        if (!item.isObject) continue
                        val name = item.path("industryName").asText(null)
                            ?: item.path("name").asText(null)
                            ?: continue
                        val weight = parseWeight(
                            item.path("weight").asText(null)
                                ?: item.path("industryWeight").asText(null)
                                ?: item.path("percentage").asText(null)
                                ?: "0"
                        )
                        val parent = item.path("sectorName").asText(null)
                            ?: item.path("sector").asText(null)
                        industries.add(IndustryEntryDto(name = name, weight = weight, parentSector = parent))
                    }
                }

                // Flat key-value object fallback
                if (sectors.isEmpty()) {
                    val iter = node.fields()
                    while (iter.hasNext()) {
                        val (key, value) = iter.next()
                        if (key in SKIP_FIELDS) continue
                        if (value.isNumber) {
                            sectors.add(SectorEntryDto(name = formatCamelCase(key), weight = value.asDouble()))
                        }
                    }
                }
            }

            return if (sectors.isNotEmpty()) {
                SectorBreakdownDto(
                    sectors = sectors.sortedByDescending { it.weight },
                    industries = industries.sortedByDescending { it.weight }
                )
            } else null
        }

        private fun findArray(node: JsonNode, vararg names: String): JsonNode? {
            for (name in names) {
                val child = node.path(name)
                if (child.isArray && child.size() > 0) return child
            }
            // Fallback: find first array in the object
            val iter = node.fields()
            while (iter.hasNext()) {
                val (_, v) = iter.next()
                if (v.isArray && v.size() > 0 && v[0].isObject) return v
            }
            return null
        }

        private fun parseWeight(str: String): Double {
            val cleaned = str.replace("%", "").trim()
            val num = cleaned.toDoubleOrNull() ?: return 0.0
            // If original had %, the value is already in percentage form
            return if (str.contains("%")) num / 100.0 else num
        }

        private fun formatCamelCase(key: String): String {
            return key.replace(Regex("([A-Z])"), " $1")
                .replaceFirstChar { it.uppercase() }
                .trim()
        }

        private fun formatEtfFieldValue(key: String, value: String): String {
            val num = value.toDoubleOrNull()
            if (num != null && key in LARGE_FIELDS) {
                val abs = kotlin.math.abs(num)
                return when {
                    abs >= 1e12 -> "$${String.format("%.2f", num / 1e12)}T"
                    abs >= 1e9 -> "$${String.format("%.2f", num / 1e9)}B"
                    abs >= 1e6 -> "$${String.format("%.2f", num / 1e6)}M"
                    else -> value
                }
            }
            return value
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
