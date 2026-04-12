package com.portfolio.service

import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Lightweight instrument data read from ingestion.instruments + ingestion.provider_raw_data.
 * Used as a replacement for the old Stock/Etf JPA entities in look-through analysis,
 * dashboard services, and exposure calculations.
 */
data class IngestionInstrument(
    val id: Long,
    val ticker: String,
    val name: String,
    val instrumentType: String,
    val isin: String?,
    val currency: String?,
    val country: String?,
    val gicsSectorCode: String? = null,
    val gicsIndustryGroupCode: String? = null
)

/**
 * ETF holding data parsed from ingestion.provider_raw_data JSONB.
 */
data class IngestionEtfHolding(
    val ticker: String?,
    val name: String?,
    val weight: BigDecimal?,
    val resolvedInstrument: IngestionInstrument? = null,
    val isEtf: Boolean = false
)

/**
 * Sector allocation entry from ingestion.provider_raw_data JSONB.
 */
data class IngestionSectorAllocation(
    val sectorName: String,
    val weight: BigDecimal
)

/**
 * Service that provides instrument lookups via cross-schema queries to the
 * ingestion schema, replacing the old Stock/Etf/EtfHolding JPA entity lookups.
 */
@Service
class IngestionInstrumentLookupService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Look up a single instrument by ticker (case-insensitive).
     * Returns null if not found. Extracts GICS codes from EODHD JSONB payload.
     */
    fun findByTicker(ticker: String): IngestionInstrument? {
        val sql = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country,
                   p.raw_payload->'General'->>'GicsSector' AS gics_sector,
                   p.raw_payload->'General'->>'GicsIndustryGroup' AS gics_industry_group
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE UPPER(i.ticker) = UPPER(:ticker) AND i.status = 'ACTIVE'
            LIMIT 1
        """.trimIndent()
        val params = MapSqlParameterSource().addValue("ticker", ticker)
        return try {
            jdbcTemplate.queryForList(sql, params).firstOrNull()?.let { mapToInstrument(it) }
        } catch (e: Exception) {
            log.debug("Failed to find instrument by ticker '{}': {}", ticker, e.message)
            null
        }
    }

    /**
     * Look up multiple instruments by their tickers (case-insensitive).
     * Returns a map of uppercase ticker -> IngestionInstrument.
     */
    fun findByTickers(tickers: Collection<String>): Map<String, IngestionInstrument> {
        if (tickers.isEmpty()) return emptyMap()
        val sql = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country,
                   p.raw_payload->'General'->>'GicsSector' AS gics_sector,
                   p.raw_payload->'General'->>'GicsIndustryGroup' AS gics_industry_group
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE UPPER(i.ticker) IN (:tickers) AND i.status = 'ACTIVE'
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("tickers", tickers.map { it.uppercase() })
        return try {
            jdbcTemplate.queryForList(sql, params)
                .mapNotNull { mapToInstrument(it) }
                .associateBy { it.ticker.uppercase() }
        } catch (e: Exception) {
            log.warn("Failed to batch-find instruments: {}", e.message)
            emptyMap()
        }
    }

    /**
     * Look up an instrument by its ingestion schema ID.
     */
    fun findById(id: Long): IngestionInstrument? {
        val sql = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country,
                   p.raw_payload->'General'->>'GicsSector' AS gics_sector,
                   p.raw_payload->'General'->>'GicsIndustryGroup' AS gics_industry_group
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE i.id = :id AND i.status = 'ACTIVE'
        """.trimIndent()
        val params = MapSqlParameterSource().addValue("id", id)
        return try {
            jdbcTemplate.queryForList(sql, params).firstOrNull()?.let { mapToInstrument(it) }
        } catch (e: Exception) {
            log.debug("Failed to find instrument by id {}: {}", id, e.message)
            null
        }
    }

    /**
     * Get ETF holdings from the EODHD JSONB payload.
     * Parses ETF_Data.Holdings and ETF_Data.Top_10_Holdings from provider_raw_data.
     * Also resolves holding tickers to IngestionInstrument where possible.
     */
    @Cacheable(value = ["etf-holdings-ingestion"], key = "#ticker")
    fun getEtfHoldings(ticker: String): List<IngestionEtfHolding> {
        val sql = """
            SELECT p.raw_payload->'ETF_Data' AS etf_data
            FROM ingestion.instruments i
            JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE UPPER(i.ticker) = UPPER(:ticker) AND i.status = 'ACTIVE'
              AND i.instrument_type = 'ETF'
            LIMIT 1
        """.trimIndent()
        val params = MapSqlParameterSource().addValue("ticker", ticker)

        return try {
            val results = jdbcTemplate.queryForList(sql, params)
            if (results.isEmpty()) return emptyList()

            val etfDataRaw = results.first()["etf_data"]
            if (etfDataRaw == null) return emptyList()

            val etfDataStr = etfDataRaw.toString()
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val etfData = objectMapper.readTree(etfDataStr)

            val holdings = mutableListOf<IngestionEtfHolding>()

            // Try Holdings first (full list), fallback to Top_10_Holdings
            val holdingsNode = etfData.path("Holdings")
            if (!holdingsNode.isMissingNode && !holdingsNode.isNull && holdingsNode.isObject) {
                holdingsNode.fields().forEach { (rawTicker, holdingNode) ->
                    val name = holdingNode.path("Name").asText(null)
                    val weightStr = holdingNode.path("Assets_%").asText(null)
                    val weight = weightStr?.toBigDecimalOrNull()?.let {
                        if (it > BigDecimal.ONE) it.divide(BigDecimal(100), 6, java.math.RoundingMode.HALF_UP) else it
                    }
                    if (rawTicker.isNotBlank() && rawTicker != "Other" && weight != null && weight > BigDecimal.ZERO) {
                        // Clean ticker: EODHD may include exchange suffix like ".US"
                        val cleanTicker = rawTicker.substringBefore(".")
                        holdings.add(IngestionEtfHolding(ticker = cleanTicker, name = name, weight = weight))
                    }
                }
            }

            if (holdings.isEmpty()) {
                // Fallback to Top_10_Holdings
                val top10 = etfData.path("Top_10_Holdings")
                if (!top10.isMissingNode && !top10.isNull && top10.isObject) {
                    top10.fields().forEach { (rawTicker, holdingNode) ->
                        val name = holdingNode.path("Name").asText(null)
                        val weightStr = holdingNode.path("Assets_%").asText(null)
                        val weight = weightStr?.toBigDecimalOrNull()?.let {
                            if (it > BigDecimal.ONE) it.divide(BigDecimal(100), 6, java.math.RoundingMode.HALF_UP) else it
                        }
                        if (rawTicker.isNotBlank() && rawTicker != "Other" && weight != null && weight > BigDecimal.ZERO) {
                            val cleanTicker = rawTicker.substringBefore(".")
                            holdings.add(IngestionEtfHolding(ticker = cleanTicker, name = name, weight = weight))
                        }
                    }
                }
            }

            if (holdings.isEmpty()) return emptyList()

            // Batch-resolve holding tickers to instruments
            val holdingTickers = holdings.mapNotNull { it.ticker }.toSet()
            val resolved = findByTickers(holdingTickers)

            holdings.map { holding ->
                val instrument = holding.ticker?.let { resolved[it.uppercase()] }
                holding.copy(
                    resolvedInstrument = instrument,
                    isEtf = instrument?.instrumentType?.uppercase() == "ETF"
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to get ETF holdings for '{}': {}", ticker, e.message)
            emptyList()
        }
    }

    /**
     * Get ETF sector allocations from EODHD JSONB payload.
     * Parses ETF_Data.Sector_Weights from provider_raw_data.
     */
    @Cacheable(value = ["etf-sector-allocations-ingestion"], key = "#ticker")
    fun getEtfSectorAllocations(ticker: String): List<IngestionSectorAllocation> {
        val sql = """
            SELECT p.raw_payload->'ETF_Data'->'Sector_Weights' AS sector_weights
            FROM ingestion.instruments i
            JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE UPPER(i.ticker) = UPPER(:ticker) AND i.status = 'ACTIVE'
              AND i.instrument_type = 'ETF'
            LIMIT 1
        """.trimIndent()
        val params = MapSqlParameterSource().addValue("ticker", ticker)

        return try {
            val results = jdbcTemplate.queryForList(sql, params)
            if (results.isEmpty()) return emptyList()

            val sectorWeightsRaw = results.first()["sector_weights"]
            if (sectorWeightsRaw == null) return emptyList()

            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val sectorWeights = objectMapper.readTree(sectorWeightsRaw.toString())

            val allocations = mutableListOf<IngestionSectorAllocation>()
            if (sectorWeights.isObject) {
                sectorWeights.fields().forEach { (sectorName, weightNode) ->
                    // EODHD Sector_Weights can be: { "Technology": {"Equity_Pct": "30.50"} }
                    // or directly: { "Technology": 30.50 }
                    val weight = if (weightNode.isObject) {
                        val equityPct = weightNode.path("Equity_Pct").asText(null)
                        equityPct?.toBigDecimalOrNull()
                    } else if (weightNode.isNumber) {
                        BigDecimal.valueOf(weightNode.asDouble())
                    } else {
                        weightNode.asText()?.toBigDecimalOrNull()
                    }

                    if (weight != null && weight > BigDecimal.ZERO) {
                        // Normalize to decimal form if given as percentage (> 1)
                        val normalizedWeight = if (weight > BigDecimal.ONE) {
                            weight.divide(BigDecimal(100), 6, java.math.RoundingMode.HALF_UP)
                        } else weight

                        allocations.add(IngestionSectorAllocation(sectorName = sectorName, weight = normalizedWeight))
                    }
                }
            }
            allocations
        } catch (e: Exception) {
            log.warn("Failed to get ETF sector allocations for '{}': {}", ticker, e.message)
            emptyList()
        }
    }

    /**
     * Get the expense ratio for an ETF from EODHD JSONB payload.
     */
    fun getEtfExpenseRatio(ticker: String): BigDecimal? {
        val sql = """
            SELECT p.raw_payload->'ETF_Data'->>'NetExpenseRatio' AS expense_ratio
            FROM ingestion.instruments i
            JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE UPPER(i.ticker) = UPPER(:ticker) AND i.status = 'ACTIVE'
              AND i.instrument_type = 'ETF'
            LIMIT 1
        """.trimIndent()
        val params = MapSqlParameterSource().addValue("ticker", ticker)

        return try {
            val results = jdbcTemplate.queryForList(sql, params)
            if (results.isEmpty()) return null
            val ratioStr = results.first()["expense_ratio"]?.toString() ?: return null
            val ratio = BigDecimal(ratioStr.replace("%", "").trim())
            // Normalize to decimal form (e.g. 0.75 -> 0.0075)
            if (ratio > BigDecimal.ONE) ratio.divide(BigDecimal(100), 6, java.math.RoundingMode.HALF_UP) else ratio
        } catch (e: Exception) {
            log.debug("Failed to get expense ratio for ETF '{}': {}", ticker, e.message)
            null
        }
    }

    private fun mapToInstrument(row: Map<String, Any?>): IngestionInstrument? {
        val id = (row["id"] as? Number)?.toLong() ?: return null
        val ticker = row["ticker"] as? String ?: return null
        val name = row["name"] as? String ?: return null
        val instrumentType = row["instrument_type"] as? String ?: return null

        // Map EODHD sector name to GICS sector code
        val rawSector = row["gics_sector"] as? String
        val gicsSectorCode = rawSector?.let { mapSectorToGicsCode(it) }

        // Map EODHD industry group name to GICS industry group code
        val rawIndustryGroup = row["gics_industry_group"] as? String
        val gicsIndustryGroupCode = rawIndustryGroup?.let { mapIndustryToGicsIGCode(it) }

        return IngestionInstrument(
            id = id,
            ticker = ticker,
            name = name,
            instrumentType = instrumentType,
            isin = row["isin"] as? String,
            currency = row["currency"] as? String,
            country = row["country"] as? String,
            gicsSectorCode = gicsSectorCode,
            gicsIndustryGroupCode = gicsIndustryGroupCode
        )
    }

    private fun mapSectorToGicsCode(sector: String): String? {
        return LookThroughService.FACTSET_SECTOR_TO_GICS[sector]
    }

    private fun mapIndustryToGicsIGCode(industry: String): String? {
        return LookThroughService.AV_INDUSTRY_TO_GICS_IG[industry.uppercase()]
    }
}
