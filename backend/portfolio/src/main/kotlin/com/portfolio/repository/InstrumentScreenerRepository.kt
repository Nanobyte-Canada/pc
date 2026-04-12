package com.portfolio.repository

import com.portfolio.dto.request.InstrumentFilterRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class InstrumentScreenerRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    companion object {
        private val VALID_SORT_DIRECTIONS = setOf("ASC", "DESC")
        private val ENRICHED_TYPES = setOf("STOCK", "ETF", "MUTUAL_FUND")

        private const val BASE_SELECT = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country, p.raw_payload
        """

        // INNER JOIN for enriched types (only show instruments with fundamentals data)
        private const val INNER_JOIN_FROM = """
            FROM ingestion.instruments i
            JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
        """

        // LEFT JOIN for sparse types (show all instruments even without fundamentals)
        private const val LEFT_JOIN_FROM = """
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
        """
    }

    private fun baseFrom(instrumentType: String): String {
        return if (instrumentType in ENRICHED_TYPES) INNER_JOIN_FROM else LEFT_JOIN_FROM
    }

    fun findInstruments(
        filter: InstrumentFilterRequest,
        sortField: String,
        sortDirection: String,
        offset: Int,
        limit: Int
    ): List<Map<String, Any?>> {
        val safeSortDirection = validateSortDirection(sortDirection)
        val sortColumn = mapSortField(filter.instrumentType, sortField)

        val params = MapSqlParameterSource()
        val whereClause = buildWhereClause(filter, params)

        val sql = """
            $BASE_SELECT
            ${baseFrom(filter.instrumentType)}
            $whereClause
            ORDER BY $sortColumn $safeSortDirection NULLS LAST
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        params.addValue("limit", limit)
        params.addValue("offset", offset)

        return jdbcTemplate.queryForList(sql, params)
    }

    fun countInstruments(filter: InstrumentFilterRequest): Long {
        val params = MapSqlParameterSource()
        val whereClause = buildWhereClause(filter, params)

        val sql = """
            SELECT COUNT(*)
            ${baseFrom(filter.instrumentType)}
            $whereClause
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, params, Long::class.java) ?: 0L
    }

    private fun buildWhereClause(filter: InstrumentFilterRequest, params: MapSqlParameterSource): String {
        val conditions = mutableListOf<String>()

        // Always-applied conditions
        conditions.add("i.instrument_type = :type")
        params.addValue("type", filter.instrumentType)

        conditions.add("i.status = 'ACTIVE'")

        // Optional text filters
        filter.tickerContains?.let {
            conditions.add("UPPER(i.ticker) LIKE UPPER(:ticker)")
            params.addValue("ticker", "%${it}%")
        }

        filter.nameContains?.let {
            conditions.add("UPPER(i.name) LIKE UPPER(:name)")
            params.addValue("name", "%${it}%")
        }

        filter.country?.let {
            conditions.add("p.raw_payload->'General'->>'CountryISO' = :country")
            params.addValue("country", it)
        }

        filter.exchange?.let {
            conditions.add("EXISTS (SELECT 1 FROM ingestion.instrument_exchanges ie JOIN ingestion.exchanges e ON ie.exchange_id = e.id WHERE ie.instrument_id = i.id AND e.code = :exchange)")
            params.addValue("exchange", it)
        }

        // Stock-specific filters
        filter.sector?.let {
            conditions.add("p.raw_payload->'General'->>'GicsSector' = :sector")
            params.addValue("sector", it)
        }

        // ETF-specific filters
        filter.issuer?.let {
            conditions.add("p.raw_payload->'ETF_Data'->>'Company_Name' = :issuer")
            params.addValue("issuer", it)
        }

        filter.assetClass?.let {
            conditions.add("p.raw_payload->'ETF_Data'->>'Asset_Category' = :assetClass")
            params.addValue("assetClass", it)
        }

        // Mutual fund-specific filters
        filter.fundCategory?.let {
            conditions.add("p.raw_payload->'MutualFund_Data'->>'Fund_Category' = :fundCategory")
            params.addValue("fundCategory", it)
        }

        filter.fundStyle?.let {
            conditions.add("p.raw_payload->'MutualFund_Data'->>'Fund_Style' = :fundStyle")
            params.addValue("fundStyle", it)
        }

        return "WHERE " + conditions.joinToString(" AND ")
    }

    private fun mapSortField(instrumentType: String, field: String): String {
        return when (field) {
            "ticker" -> "i.ticker"
            "name" -> "i.name"
            "country" -> "i.country"
            "marketCap" -> "(p.raw_payload->'Highlights'->>'MarketCapitalizationMln')::numeric"
            "pe" -> "(p.raw_payload->'Highlights'->>'PERatio')::numeric"
            "eps" -> "(p.raw_payload->'Highlights'->>'DilutedEpsTTM')::numeric"
            "dividendYield" -> "(p.raw_payload->'Highlights'->>'DividendYield')::numeric"
            "beta" -> "(p.raw_payload->'Technicals'->>'Beta')::numeric"
            "expenseRatio" -> "(p.raw_payload->'ETF_Data'->>'NetExpenseRatio')::numeric"
            "totalAssets" -> "(p.raw_payload->'ETF_Data'->>'TotalAssets')::numeric"
            "yield" -> "COALESCE((p.raw_payload->'ETF_Data'->>'Yield')::numeric, (p.raw_payload->'MutualFund_Data'->>'Yield')::numeric)"
            "nav" -> "(p.raw_payload->'MutualFund_Data'->>'Nav')::numeric"
            else -> "i.ticker"
        }
    }

    private fun validateSortDirection(direction: String): String {
        val normalized = direction.uppercase()
        require(normalized in VALID_SORT_DIRECTIONS) {
            "Invalid sort direction: '$direction'. Must be ASC or DESC."
        }
        return normalized
    }
}
