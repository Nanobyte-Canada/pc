package com.portfolio.ingestion.client.etfcom

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.ingestion.dto.etfcom.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class EtfComResponseParser {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Parse fundSummaryData response node.
     * Expected structure: { "fields": [ {"name": "issuer", "value": "Vanguard", "label": "..."}, ... ] }
     */
    fun parseFundSummary(node: JsonNode): EtfComFundSummaryResponse {
        val fieldsMap = extractFieldsMap(node)
        if (fieldsMap.isEmpty()) {
            log.debug("Fund summary: no fields found, node structure: {}", node.toString().take(500))
        }
        return EtfComFundSummaryResponse(
            issuer = fieldsMap["issuer"],
            inceptionDate = parseInceptionDate(fieldsMap["inceptionDate"]),
            expenseRatio = parsePercentToBigDecimal(fieldsMap["expenseRatio"]),
            aum = parseAumToBigDecimal(fieldsMap["aum"]),
            indexTracked = fieldsMap["indexTracked"] ?: fieldsMap["index"],
            segment = fieldsMap["segmentBenchmark"] ?: fieldsMap["segment"],
            description = fieldsMap["description"],
            fundName = fieldsMap["fundName"]
        )
    }

    /**
     * Parse topHoldings response node.
     * Expected structure: { "data": [ {"name": "numberOfHoldings", "value": 507}, {"name": "all_holdings", "data": [...]} ] }
     */
    fun parseTopHoldings(node: JsonNode): EtfComTopHoldingsResponse {
        val dataArray = node.path("data")
        if (!dataArray.isArray) {
            log.debug("Top holdings: no data array found, node structure: {}", node.toString().take(500))
            return EtfComTopHoldingsResponse()
        }

        var numberOfHoldings: Int? = null
        val holdings = mutableListOf<EtfComHolding>()
        var asOfDate: String? = null

        for (entry in dataArray) {
            val name = entry.path("name").asText("")
            when (name) {
                "numberOfHoldings" -> numberOfHoldings = entry.path("value").asInt(0)
                "all_holdings" -> {
                    val holdingsData = entry.path("data")
                    if (holdingsData.isArray) {
                        for (h in holdingsData) {
                            val symbol = h.path("symbol").asText(null) ?: continue
                            val holdingName = h.path("name").asText(null)
                            val weight = parsePercentToBigDecimal(h.path("weight").asText(null))
                            val holdingAsOf = parseIsoTimestampToDate(h.path("asOf").asText(null))
                            if (asOfDate == null && holdingAsOf != null) {
                                asOfDate = holdingAsOf
                            }
                            holdings.add(
                                EtfComHolding(
                                    symbol = symbol,
                                    holdingName = holdingName,
                                    weight = weight,
                                    asOfDate = holdingAsOf
                                )
                            )
                        }
                    }
                }
            }
        }

        return EtfComTopHoldingsResponse(
            holdings = holdings.ifEmpty { null },
            asOfDate = asOfDate,
            numberOfHoldings = numberOfHoldings
        )
    }

    /**
     * Parse sectorIndustryBreakdown response node.
     * Best-effort: tries both fields[] and data[] patterns.
     */
    fun parseSectorBreakdown(node: JsonNode): EtfComSectorBreakdownResponse {
        log.debug("Sector breakdown node structure: {}", node.toString().take(500))
        val sectors = mutableListOf<EtfComSector>()

        // Try data[] array pattern
        val dataArray = node.path("data")
        if (dataArray.isArray) {
            for (entry in dataArray) {
                val sectorName = entry.path("name").asText(null)
                    ?: entry.path("sectorName").asText(null)
                    ?: continue
                val weight = parseMaybePercentToBigDecimal(entry)
                sectors.add(EtfComSector(sectorName = sectorName, weight = weight))
            }
        }

        // Try fields[] array pattern
        if (sectors.isEmpty()) {
            val fieldsArray = node.path("fields")
            if (fieldsArray.isArray) {
                for (entry in fieldsArray) {
                    val sectorName = entry.path("name").asText(null) ?: continue
                    val weight = parsePercentToBigDecimal(entry.path("value").asText(null))
                    sectors.add(EtfComSector(sectorName = sectorName, weight = weight))
                }
            }
        }

        return EtfComSectorBreakdownResponse(sectors = sectors.ifEmpty { null })
    }

    /**
     * Parse performanceData response node.
     * Best-effort: tries fields[] and data[] patterns.
     */
    fun parsePerformance(node: JsonNode): EtfComPerformanceResponse {
        log.debug("Performance node structure: {}", node.toString().take(500))
        val fieldsMap = extractFieldsMap(node)

        if (fieldsMap.isNotEmpty()) {
            return EtfComPerformanceResponse(
                nav = extractPerformanceReturns(fieldsMap, "nav"),
                price = extractPerformanceReturns(fieldsMap, "price"),
                asOfDate = fieldsMap["asOfDate"]
            )
        }

        // Try data[] pattern
        val dataArray = node.path("data")
        if (dataArray.isArray) {
            val dataMap = mutableMapOf<String, String>()
            for (entry in dataArray) {
                val name = entry.path("name").asText(null) ?: continue
                val value = entry.path("value").asText(null) ?: continue
                dataMap[name] = value
            }
            if (dataMap.isNotEmpty()) {
                return EtfComPerformanceResponse(
                    nav = extractPerformanceReturns(dataMap, "nav"),
                    price = extractPerformanceReturns(dataMap, "price"),
                    asOfDate = dataMap["asOfDate"]
                )
            }
        }

        return EtfComPerformanceResponse()
    }

    /**
     * Parse fundPortfolioData response node.
     * Best-effort: tries fields[] and data[] patterns.
     */
    fun parsePortfolioData(node: JsonNode): EtfComPortfolioDataResponse {
        log.debug("Portfolio data node structure: {}", node.toString().take(500))
        val fieldsMap = extractFieldsMap(node)

        if (fieldsMap.isNotEmpty()) {
            return EtfComPortfolioDataResponse(
                weightedAvgMarketCap = parseNumericValue(fieldsMap["weightedAvgMarketCap"]),
                priceToEarnings = parseNumericValue(fieldsMap["priceToEarnings"] ?: fieldsMap["peRatio"]),
                priceToBook = parseNumericValue(fieldsMap["priceToBook"] ?: fieldsMap["pbRatio"]),
                dividendYield = parsePercentToBigDecimal(fieldsMap["dividendYield"])
            )
        }

        // Try data[] pattern
        val dataArray = node.path("data")
        if (dataArray.isArray) {
            val dataMap = mutableMapOf<String, String>()
            for (entry in dataArray) {
                val name = entry.path("name").asText(null) ?: continue
                val value = entry.path("value").asText(null) ?: continue
                dataMap[name] = value
            }
            if (dataMap.isNotEmpty()) {
                return EtfComPortfolioDataResponse(
                    weightedAvgMarketCap = parseNumericValue(dataMap["weightedAvgMarketCap"]),
                    priceToEarnings = parseNumericValue(dataMap["priceToEarnings"] ?: dataMap["peRatio"]),
                    priceToBook = parseNumericValue(dataMap["priceToBook"] ?: dataMap["pbRatio"]),
                    dividendYield = parsePercentToBigDecimal(dataMap["dividendYield"])
                )
            }
        }

        return EtfComPortfolioDataResponse()
    }

    // ========================================
    // Helpers
    // ========================================

    /**
     * Extracts a map of name->value from a "fields" array in the node.
     */
    private fun extractFieldsMap(node: JsonNode): Map<String, String> {
        val fieldsArray = node.path("fields")
        if (!fieldsArray.isArray) return emptyMap()

        val map = mutableMapOf<String, String>()
        for (field in fieldsArray) {
            val name = field.path("name").asText(null) ?: continue
            val value = field.path("value").asText(null) ?: continue
            map[name] = value
        }
        return map
    }

    /**
     * Parse inception date from "MM/dd/yy" or other formats to "yyyy-MM-dd".
     */
    private fun parseInceptionDate(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "-" || raw == "None") return null
        return try {
            val date = LocalDate.parse(raw, DateTimeFormatter.ofPattern("MM/dd/yy"))
            date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            try {
                val date = LocalDate.parse(raw, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e2: Exception) {
                // Return as-is if already in a parseable format
                raw
            }
        }
    }

    /**
     * Strip "%" and parse to BigDecimal (as fraction, e.g., "0.03%" -> 0.0003).
     * If value doesn't contain "%", tries to parse as-is.
     */
    private fun parsePercentToBigDecimal(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank() || raw == "-" || raw == "N/A" || raw == "None") return null
        return try {
            val cleaned = raw.replace("%", "").replace(",", "").trim()
            BigDecimal(cleaned).divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            log.trace("Could not parse percent value: {}", raw)
            null
        }
    }

    /**
     * Parse AUM string like "$871.22B", "$150.5M", "$2.3K" to BigDecimal in raw units.
     */
    private fun parseAumToBigDecimal(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank() || raw == "-" || raw == "N/A" || raw == "None") return null
        return try {
            val cleaned = raw.replace("$", "").replace(",", "").trim()
            val suffix = cleaned.last().uppercaseChar()
            val multiplier = when (suffix) {
                'T' -> BigDecimal("1000000000000")
                'B' -> BigDecimal("1000000000")
                'M' -> BigDecimal("1000000")
                'K' -> BigDecimal("1000")
                else -> null
            }
            if (multiplier != null) {
                val number = BigDecimal(cleaned.dropLast(1))
                number.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal(cleaned)
            }
        } catch (e: Exception) {
            log.trace("Could not parse AUM value: {}", raw)
            null
        }
    }

    /**
     * Parse ISO timestamp like "2026-01-30T00:00:00.000Z" to "yyyy-MM-dd".
     */
    private fun parseIsoTimestampToDate(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "-") return null
        return try {
            val odt = OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            odt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            try {
                // Try plain date
                LocalDate.parse(raw.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
                raw.take(10)
            } catch (e2: Exception) {
                raw
            }
        }
    }

    private fun parseNumericValue(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank() || raw == "-" || raw == "N/A" || raw == "None") return null
        return try {
            val cleaned = raw.replace("$", "").replace(",", "").replace("%", "").trim()
            // Handle suffix like B, M, K
            val suffix = cleaned.last().uppercaseChar()
            val multiplier = when (suffix) {
                'T' -> BigDecimal("1000000000000")
                'B' -> BigDecimal("1000000000")
                'M' -> BigDecimal("1000000")
                'K' -> BigDecimal("1000")
                else -> null
            }
            if (multiplier != null) {
                BigDecimal(cleaned.dropLast(1)).multiply(multiplier)
            } else {
                BigDecimal(cleaned)
            }
        } catch (e: Exception) {
            log.trace("Could not parse numeric value: {}", raw)
            null
        }
    }

    private fun parseMaybePercentToBigDecimal(entry: JsonNode): BigDecimal? {
        val valueNode = entry.path("value")
        if (valueNode.isMissingNode || valueNode.isNull) return null
        return if (valueNode.isNumber) {
            valueNode.decimalValue()
        } else {
            parsePercentToBigDecimal(valueNode.asText(null))
        }
    }

    private fun extractPerformanceReturns(map: Map<String, String>, prefix: String): EtfComPerformanceReturns? {
        val oneMonth = parsePercentToBigDecimal(map["${prefix}OneMonth"] ?: map["${prefix}_1m"])
        val threeMonth = parsePercentToBigDecimal(map["${prefix}ThreeMonth"] ?: map["${prefix}_3m"])
        val ytd = parsePercentToBigDecimal(map["${prefix}Ytd"] ?: map["${prefix}_ytd"])
        val oneYear = parsePercentToBigDecimal(map["${prefix}OneYear"] ?: map["${prefix}_1y"])
        val threeYear = parsePercentToBigDecimal(map["${prefix}ThreeYear"] ?: map["${prefix}_3y"])
        val fiveYear = parsePercentToBigDecimal(map["${prefix}FiveYear"] ?: map["${prefix}_5y"])
        return if (listOf(oneMonth, threeMonth, ytd, oneYear, threeYear, fiveYear).all { it == null }) {
            null
        } else {
            EtfComPerformanceReturns(
                oneMonth = oneMonth,
                threeMonth = threeMonth,
                ytd = ytd,
                oneYear = oneYear,
                threeYear = threeYear,
                fiveYear = fiveYear
            )
        }
    }
}
