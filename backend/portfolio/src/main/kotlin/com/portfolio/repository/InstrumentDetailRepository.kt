package com.portfolio.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class InstrumentDetailRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    companion object {
        private const val BASE_SELECT = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country, p.raw_payload
        """

        private const val BASE_FROM = """
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
        """

        private val JSONB_FIELD_PATHS = mapOf(
            "sector" to "p.raw_payload->'General'->>'GicsSector'",
            "country" to "p.raw_payload->'General'->>'CountryISO'",
            "issuer" to "p.raw_payload->'ETF_Data'->>'Company_Name'",
            "assetClass" to "p.raw_payload->'ETF_Data'->>'Asset_Category'",
            "fundCategory" to "p.raw_payload->'MutualFund_Data'->>'Fund_Category'",
            "fundStyle" to "p.raw_payload->'MutualFund_Data'->>'Fund_Style'"
        )
    }

    fun findByTickerAndType(ticker: String, instrumentType: String): Map<String, Any?>? {
        val sql = """
            $BASE_SELECT
            $BASE_FROM
            WHERE UPPER(i.ticker) = UPPER(:ticker)
              AND i.instrument_type = :type
              AND i.status = 'ACTIVE'
            LIMIT 1
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("ticker", ticker)
            .addValue("type", instrumentType)

        val results = jdbcTemplate.queryForList(sql, params)
        return results.firstOrNull()
    }

    fun searchInstruments(query: String, types: List<String>?, limit: Int): List<Map<String, Any?>> {
        val params = MapSqlParameterSource()
            .addValue("query", query)
            .addValue("queryPrefix", "$query%")
            .addValue("queryContains", "%$query%")
            .addValue("limit", limit)

        val typeFilter = if (!types.isNullOrEmpty()) {
            params.addValue("types", types)
            "AND i.instrument_type IN (:types)"
        } else {
            ""
        }

        val sql = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country,
                CASE
                    WHEN UPPER(i.ticker) = UPPER(:query) THEN 'TICKER_EXACT'
                    WHEN UPPER(i.ticker) LIKE UPPER(:queryPrefix) THEN 'TICKER_PREFIX'
                    WHEN UPPER(i.isin) = UPPER(:query) THEN 'IDENTIFIER_EXACT'
                    ELSE 'NAME_CONTAINS'
                END AS match_type,
                CASE
                    WHEN UPPER(i.ticker) = UPPER(:query) THEN 0
                    WHEN UPPER(i.isin) = UPPER(:query) THEN 1
                    WHEN UPPER(i.ticker) LIKE UPPER(:queryPrefix) THEN 2
                    ELSE 3
                END AS match_priority
            FROM ingestion.instruments i
            WHERE i.status = 'ACTIVE'
              $typeFilter
              AND (
                  UPPER(i.ticker) = UPPER(:query)
                  OR UPPER(i.ticker) LIKE UPPER(:queryPrefix)
                  OR UPPER(i.isin) = UPPER(:query)
                  OR UPPER(i.name) LIKE UPPER(:queryContains)
              )
            ORDER BY match_priority, i.ticker
            LIMIT :limit
        """.trimIndent()

        return jdbcTemplate.queryForList(sql, params)
    }

    fun getDistinctValues(field: String, instrumentType: String): List<String> {
        val jsonPath = JSONB_FIELD_PATHS[field] ?: return emptyList()

        val sql = """
            SELECT DISTINCT $jsonPath AS val
            $BASE_FROM
            WHERE i.instrument_type = :type
              AND i.status = 'ACTIVE'
              AND $jsonPath IS NOT NULL
              AND $jsonPath != ''
            ORDER BY val
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("type", instrumentType)

        return jdbcTemplate.queryForList(sql, params, String::class.java)
    }

    fun countByType(): Map<String, Long> {
        val sql = """
            SELECT instrument_type, COUNT(*) AS cnt
            FROM ingestion.instruments
            WHERE status = 'ACTIVE'
            GROUP BY instrument_type
            ORDER BY instrument_type
        """.trimIndent()

        val results = jdbcTemplate.queryForList(sql, MapSqlParameterSource())
        return results.associate { row ->
            (row["instrument_type"] as String) to (row["cnt"] as Long)
        }
    }
}
