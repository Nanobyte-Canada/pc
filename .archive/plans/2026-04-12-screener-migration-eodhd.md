# Screener Migration to EODHD Data — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate all screener and instrument detail functionality from old `public.stocks`/`public.etfs` tables to new `ingestion.instruments` + `ingestion.provider_raw_data` (EODHD JSONB), add screeners for 6 instrument types, redesign screener UI with sidebar navigation, and build rich visual detail pages with AG Charts.

**Architecture:** Cross-schema native SQL queries from main backend to `ingestion.*` tables. No new tables — JSONB operators extract fields from `provider_raw_data.raw_payload`. Shared frontend components with type-specific configurations for screener columns, filters, and detail page sections.

**Tech Stack:** Kotlin/Spring Boot, PostgreSQL JSONB queries, React/TypeScript, AG Grid, AG Charts, React Query, CSS custom properties

**Design Spec:** `docs/superpowers/specs/2026-04-12-screener-migration-eodhd-design.md`
**Visual Mockups:** `.superpowers/brainstorm/19683-1775999357/content/` (stock, ETF, mutual fund detail pages + screener layouts)

---

## Phase 1: Backend — Cross-Schema Data Access Layer

### Task 1: Instrument Screener Repository (Native SQL)

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/repository/InstrumentScreenerRepository.kt`
- Create: `backend/src/main/kotlin/com/portfolio/dto/request/InstrumentFilterRequest.kt`

- [ ] **Step 1: Create filter request DTOs for all 6 instrument types**

```kotlin
// InstrumentFilterRequest.kt
package com.portfolio.dto.request

data class InstrumentFilterRequest(
    val instrumentType: String,  // STOCK, ETF, MUTUAL_FUND, etc.
    val tickerContains: String? = null,
    val nameContains: String? = null,
    val country: String? = null,
    val exchange: String? = null,
    // Stock-specific
    val sector: String? = null,
    // ETF-specific
    val issuer: String? = null,
    val assetClass: String? = null,
    // Mutual fund-specific
    val fundCategory: String? = null,
    val fundStyle: String? = null
)
```

- [ ] **Step 2: Create repository interface with native query for screener**

The repository uses a Spring `JdbcTemplate` approach instead of JPA `@Query` because the JSONB filter conditions are dynamic per instrument type. Use `NamedParameterJdbcTemplate` for safe parameterization.

```kotlin
// InstrumentScreenerRepository.kt
package com.portfolio.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class InstrumentScreenerRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun findInstruments(
        filter: InstrumentFilterRequest,
        sortField: String,
        sortDirection: String,
        offset: Int,
        limit: Int
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf("i.instrument_type = :type", "i.status = 'ACTIVE'")
        val params = mutableMapOf<String, Any>("type" to filter.instrumentType)

        filter.tickerContains?.let {
            conditions.add("UPPER(i.ticker) LIKE UPPER(:ticker)")
            params["ticker"] = "%$it%"
        }
        filter.nameContains?.let {
            conditions.add("UPPER(i.name) LIKE UPPER(:name)")
            params["name"] = "%$it%"
        }
        // Stock filters
        filter.sector?.let {
            conditions.add("p.raw_payload->'General'->>'GicSector' = :sector")
            params["sector"] = it
        }
        filter.country?.let {
            conditions.add("p.raw_payload->'General'->>'CountryISO' = :country")
            params["country"] = it
        }
        // ETF filters
        filter.issuer?.let {
            conditions.add("p.raw_payload->'ETF_Data'->>'Company_Name' = :issuer")
            params["issuer"] = it
        }
        filter.assetClass?.let {
            conditions.add("p.raw_payload->'ETF_Data'->>'Asset_Category' = :assetClass")
            params["assetClass"] = it
        }
        // Mutual fund filters
        filter.fundCategory?.let {
            conditions.add("p.raw_payload->'MutualFund_Data'->>'Fund_Category' = :fundCategory")
            params["fundCategory"] = it
        }
        filter.fundStyle?.let {
            conditions.add("p.raw_payload->'MutualFund_Data'->>'Fund_Style' = :fundStyle")
            params["fundStyle"] = it
        }

        val whereClause = conditions.joinToString(" AND ")
        val sortColumn = mapSortField(filter.instrumentType, sortField)

        val sql = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country,
                   p.raw_payload
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p 
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE $whereClause
            ORDER BY $sortColumn $sortDirection NULLS LAST
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        params["limit"] = limit
        params["offset"] = offset

        return jdbcTemplate.queryForList(sql, params)
    }

    fun countInstruments(filter: InstrumentFilterRequest): Long {
        val conditions = mutableListOf("i.instrument_type = :type", "i.status = 'ACTIVE'")
        val params = mutableMapOf<String, Any>("type" to filter.instrumentType)

        // Same filter logic as findInstruments (extract to shared method)
        filter.tickerContains?.let {
            conditions.add("UPPER(i.ticker) LIKE UPPER(:ticker)")
            params["ticker"] = "%$it%"
        }
        filter.nameContains?.let {
            conditions.add("UPPER(i.name) LIKE UPPER(:name)")
            params["name"] = "%$it%"
        }
        filter.sector?.let {
            conditions.add("p.raw_payload->'General'->>'GicSector' = :sector")
            params["sector"] = it
        }
        filter.country?.let {
            conditions.add("p.raw_payload->'General'->>'CountryISO' = :country")
            params["country"] = it
        }
        filter.issuer?.let {
            conditions.add("p.raw_payload->'ETF_Data'->>'Company_Name' = :issuer")
            params["issuer"] = it
        }
        filter.assetClass?.let {
            conditions.add("p.raw_payload->'ETF_Data'->>'Asset_Category' = :assetClass")
            params["assetClass"] = it
        }
        filter.fundCategory?.let {
            conditions.add("p.raw_payload->'MutualFund_Data'->>'Fund_Category' = :fundCategory")
            params["fundCategory"] = it
        }
        filter.fundStyle?.let {
            conditions.add("p.raw_payload->'MutualFund_Data'->>'Fund_Style' = :fundStyle")
            params["fundStyle"] = it
        }

        val whereClause = conditions.joinToString(" AND ")

        val sql = """
            SELECT COUNT(*)
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p 
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE $whereClause
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, params, Long::class.java) ?: 0
    }

    private fun mapSortField(type: String, field: String): String {
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
}
```

- [ ] **Step 3: Verify it compiles in Docker**

Run: `docker compose exec backend ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/repository/InstrumentScreenerRepository.kt
git add backend/src/main/kotlin/com/portfolio/dto/request/InstrumentFilterRequest.kt
git commit -m "feat: add InstrumentScreenerRepository with cross-schema JSONB queries"
```

---

### Task 2: Screener Response DTOs

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/dto/response/InstrumentScreenerDto.kt`

- [ ] **Step 1: Create screener DTOs that extract JSONB fields per type**

```kotlin
// InstrumentScreenerDto.kt
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
    val sector: String? = null,
    val marketCap: Double? = null,
    val pe: Double? = null,
    val eps: Double? = null,
    val dividendYield: Double? = null,
    val weekHigh52: Double? = null,
    val weekLow52: Double? = null,
    val beta: Double? = null,
    // ETF fields
    val issuer: String? = null,
    val assetClass: String? = null,
    val expenseRatio: Double? = null,
    val yield: Double? = null,
    val totalAssets: Double? = null,
    val holdingsCount: Int? = null,
    val return1Y: Double? = null,
    // Mutual fund fields
    val fundCategory: String? = null,
    val fundStyle: String? = null,
    val nav: Double? = null
) {
    companion object {
        private val objectMapper = ObjectMapper()

        fun fromRow(row: Map<String, Any?>, instrumentType: String): InstrumentScreenerDto {
            val rawPayload = parsePayload(row["raw_payload"])

            return InstrumentScreenerDto(
                id = (row["id"] as Number).toLong(),
                ticker = row["ticker"] as String,
                name = row["name"] as String,
                instrumentType = instrumentType,
                isin = row["isin"] as? String,
                currency = row["currency"] as? String,
                country = row["country"] as? String,
                exchange = rawPayload?.path("General")?.path("Exchange")?.asTextOrNull(),
                // Stock
                sector = rawPayload?.path("General")?.path("GicSector")?.asTextOrNull(),
                marketCap = rawPayload?.path("Highlights")?.path("MarketCapitalizationMln")?.asDoubleOrNull(),
                pe = rawPayload?.path("Highlights")?.path("PERatio")?.asDoubleOrNull(),
                eps = rawPayload?.path("Highlights")?.path("DilutedEpsTTM")?.asDoubleOrNull(),
                dividendYield = rawPayload?.path("Highlights")?.path("DividendYield")?.asDoubleOrNull(),
                weekHigh52 = rawPayload?.path("Technicals")?.path("52WeekHigh")?.asDoubleOrNull(),
                weekLow52 = rawPayload?.path("Technicals")?.path("52WeekLow")?.asDoubleOrNull(),
                beta = rawPayload?.path("Technicals")?.path("Beta")?.asDoubleOrNull(),
                // ETF
                issuer = rawPayload?.path("ETF_Data")?.path("Company_Name")?.asTextOrNull(),
                assetClass = rawPayload?.path("ETF_Data")?.path("Asset_Category")?.asTextOrNull(),
                expenseRatio = rawPayload?.path("ETF_Data")?.path("NetExpenseRatio")?.asTextOrNull()?.toDoubleOrNull(),
                yield = rawPayload?.path("ETF_Data")?.path("Yield")?.asTextOrNull()?.toDoubleOrNull()
                    ?: rawPayload?.path("MutualFund_Data")?.path("Yield")?.asTextOrNull()?.toDoubleOrNull(),
                totalAssets = rawPayload?.path("ETF_Data")?.path("TotalAssets")?.asTextOrNull()?.toDoubleOrNull(),
                holdingsCount = rawPayload?.path("ETF_Data")?.path("Holdings_Count")?.asIntOrNull(),
                return1Y = rawPayload?.path("ETF_Data")?.path("Performance")?.path("Returns_1Y")?.asTextOrNull()?.toDoubleOrNull()
                    ?: rawPayload?.path("MutualFund_Data")?.path("Yield_1Year_YTD")?.asTextOrNull()?.toDoubleOrNull(),
                // Mutual fund
                fundCategory = rawPayload?.path("MutualFund_Data")?.path("Fund_Category")?.asTextOrNull(),
                fundStyle = rawPayload?.path("MutualFund_Data")?.path("Fund_Style")?.asTextOrNull(),
                nav = rawPayload?.path("MutualFund_Data")?.path("Nav")?.asTextOrNull()?.toDoubleOrNull()
            )
        }

        private fun parsePayload(value: Any?): JsonNode? {
            if (value == null) return null
            return when (value) {
                is String -> objectMapper.readTree(value)
                is JsonNode -> value
                else -> objectMapper.readTree(value.toString())
            }
        }

        private fun JsonNode.asTextOrNull(): String? =
            if (isNull || isMissingNode || asText() == "null" || asText() == "None") null else asText()

        private fun JsonNode.asDoubleOrNull(): Double? =
            asTextOrNull()?.toDoubleOrNull()

        private fun JsonNode.asIntOrNull(): Int? =
            if (isNull || isMissingNode) null else if (isNumber) asInt() else asText()?.toIntOrNull()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `docker compose exec backend ./gradlew compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/dto/response/InstrumentScreenerDto.kt
git commit -m "feat: add InstrumentScreenerDto with JSONB field extraction"
```

---

### Task 3: Instrument Detail DTOs

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/dto/response/InstrumentDetailDto.kt`

- [ ] **Step 1: Create detail DTOs — general info shared, type-specific sections**

The detail page sends the full parsed JSONB payload organized into sections. Rather than mapping every field, send the relevant sections as `Map<String, Any?>` and let the frontend handle rendering. This avoids maintaining 100+ DTO fields and stays flexible.

```kotlin
// InstrumentDetailDto.kt
package com.portfolio.dto.response

data class InstrumentDetailDto(
    val id: Long,
    val ticker: String,
    val name: String,
    val instrumentType: String,
    val isin: String?,
    val currency: String?,
    val country: String?,
    // Parsed EODHD sections — nullable per type
    val general: Map<String, Any?>? = null,
    val highlights: Map<String, Any?>? = null,
    val valuation: Map<String, Any?>? = null,
    val technicals: Map<String, Any?>? = null,
    val financials: Map<String, Any?>? = null,
    val earnings: Map<String, Any?>? = null,
    val splitsDividends: Map<String, Any?>? = null,
    val sharesStats: Map<String, Any?>? = null,
    val analystRatings: Map<String, Any?>? = null,
    val etfData: Map<String, Any?>? = null,
    val mutualFundData: Map<String, Any?>? = null
)
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/dto/response/InstrumentDetailDto.kt
git commit -m "feat: add InstrumentDetailDto with section-based EODHD data"
```

---

### Task 4: Instrument Detail Repository

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/repository/InstrumentDetailRepository.kt`

- [ ] **Step 1: Create repository that fetches instrument + raw payload by ticker**

```kotlin
// InstrumentDetailRepository.kt
package com.portfolio.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class InstrumentDetailRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun findByTickerAndType(ticker: String, instrumentType: String): Map<String, Any?>? {
        val sql = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country,
                   p.raw_payload
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p 
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE UPPER(i.ticker) = UPPER(:ticker) 
              AND i.instrument_type = :type
              AND i.status = 'ACTIVE'
            LIMIT 1
        """.trimIndent()

        val results = jdbcTemplate.queryForList(sql, mapOf("ticker" to ticker, "type" to instrumentType))
        return results.firstOrNull()
    }

    fun searchInstruments(query: String, types: List<String>?, limit: Int): List<Map<String, Any?>> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>("limit" to limit)

        if (!types.isNullOrEmpty()) {
            conditions.add("i.instrument_type IN (:types)")
            params["types"] = types
        }
        conditions.add("i.status = 'ACTIVE'")

        val whereClause = conditions.joinToString(" AND ")

        val sql = """
            SELECT i.id, i.ticker, i.name, i.instrument_type, i.isin, i.currency, i.country,
                   CASE 
                       WHEN UPPER(i.ticker) = UPPER(:query) THEN 'TICKER_EXACT'
                       WHEN UPPER(i.ticker) LIKE UPPER(:queryPrefix) THEN 'TICKER_PREFIX'
                       WHEN UPPER(i.isin) = UPPER(:query) THEN 'IDENTIFIER_EXACT'
                       ELSE 'NAME_CONTAINS'
                   END as match_type
            FROM ingestion.instruments i
            WHERE $whereClause
              AND (
                  UPPER(i.ticker) LIKE UPPER(:queryPrefix)
                  OR UPPER(i.name) LIKE UPPER(:queryContains)
                  OR UPPER(i.isin) = UPPER(:query)
              )
            ORDER BY 
                CASE 
                    WHEN UPPER(i.ticker) = UPPER(:query) THEN 0
                    WHEN UPPER(i.isin) = UPPER(:query) THEN 1
                    WHEN UPPER(i.ticker) LIKE UPPER(:queryPrefix) THEN 2
                    ELSE 3
                END,
                i.ticker
            LIMIT :limit
        """.trimIndent()

        params["query"] = query
        params["queryPrefix"] = "$query%"
        params["queryContains"] = "%$query%"

        return jdbcTemplate.queryForList(sql, params)
    }

    fun getDistinctValues(field: String, instrumentType: String): List<String> {
        val jsonPath = when (field) {
            "sector" -> "p.raw_payload->'General'->>'GicSector'"
            "country" -> "p.raw_payload->'General'->>'CountryISO'"
            "issuer" -> "p.raw_payload->'ETF_Data'->>'Company_Name'"
            "assetClass" -> "p.raw_payload->'ETF_Data'->>'Asset_Category'"
            "fundCategory" -> "p.raw_payload->'MutualFund_Data'->>'Fund_Category'"
            "fundStyle" -> "p.raw_payload->'MutualFund_Data'->>'Fund_Style'"
            "exchange" -> "i.country"
            else -> return emptyList()
        }

        val sql = """
            SELECT DISTINCT $jsonPath as val
            FROM ingestion.instruments i
            LEFT JOIN ingestion.provider_raw_data p 
                ON p.instrument_id = i.id AND p.provider = 'EODHD' AND p.data_type = 'FUNDAMENTALS'
            WHERE i.instrument_type = :type AND i.status = 'ACTIVE'
              AND $jsonPath IS NOT NULL AND $jsonPath != ''
            ORDER BY val
        """.trimIndent()

        return jdbcTemplate.queryForList(sql, mapOf("type" to instrumentType), String::class.java)
    }

    fun countByType(): Map<String, Long> {
        val sql = """
            SELECT instrument_type, COUNT(*) as cnt
            FROM ingestion.instruments
            WHERE status = 'ACTIVE'
            GROUP BY instrument_type
            ORDER BY instrument_type
        """.trimIndent()

        return jdbcTemplate.queryForList(sql, emptyMap<String, Any>())
            .associate { (it["instrument_type"] as String) to (it["cnt"] as Number).toLong() }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `docker compose exec backend ./gradlew compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/repository/InstrumentDetailRepository.kt
git commit -m "feat: add InstrumentDetailRepository for detail, search, and reference data"
```

---

### Task 5: Instrument Screener Service

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/service/InstrumentScreenerService.kt`

- [ ] **Step 1: Create service with screener, detail, search, and reference data methods**

```kotlin
// InstrumentScreenerService.kt
package com.portfolio.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.dto.request.InstrumentFilterRequest
import com.portfolio.dto.response.*
import com.portfolio.repository.InstrumentDetailRepository
import com.portfolio.repository.InstrumentScreenerRepository
import org.springframework.stereotype.Service

@Service
class InstrumentScreenerService(
    private val screenerRepository: InstrumentScreenerRepository,
    private val detailRepository: InstrumentDetailRepository,
    private val objectMapper: ObjectMapper
) {
    fun filterInstruments(
        filter: InstrumentFilterRequest,
        page: Int,
        size: Int,
        sortField: String,
        sortDirection: String
    ): PagedResponseDto<InstrumentScreenerDto> {
        val clampedSize = size.coerceIn(1, 100)
        val offset = page * clampedSize

        val rows = screenerRepository.findInstruments(filter, sortField, sortDirection, offset, clampedSize)
        val total = screenerRepository.countInstruments(filter)
        val totalPages = if (total == 0L) 0 else ((total + clampedSize - 1) / clampedSize).toInt()

        val data = rows.map { InstrumentScreenerDto.fromRow(it, filter.instrumentType) }

        return PagedResponseDto(
            data = data,
            meta = PageMetaDto(
                page = page,
                size = clampedSize,
                totalElements = total,
                totalPages = totalPages
            )
        )
    }

    fun getInstrumentDetail(ticker: String, instrumentType: String): InstrumentDetailDto? {
        val row = detailRepository.findByTickerAndType(ticker, instrumentType) ?: return null
        val payload = parsePayload(row["raw_payload"])

        return InstrumentDetailDto(
            id = (row["id"] as Number).toLong(),
            ticker = row["ticker"] as String,
            name = row["name"] as String,
            instrumentType = instrumentType,
            isin = row["isin"] as? String,
            currency = row["currency"] as? String,
            country = row["country"] as? String,
            general = payload?.get("General")?.toMap(),
            highlights = payload?.get("Highlights")?.toMap(),
            valuation = payload?.get("Valuation")?.toMap(),
            technicals = payload?.get("Technicals")?.toMap(),
            financials = payload?.get("Financials")?.toMap(),
            earnings = payload?.get("Earnings")?.toMap(),
            splitsDividends = payload?.get("SplitsDividends")?.toMap(),
            sharesStats = payload?.get("SharesStats")?.toMap(),
            analystRatings = payload?.get("AnalystRatings")?.toMap(),
            etfData = payload?.get("ETF_Data")?.toMap(),
            mutualFundData = payload?.get("MutualFund_Data")?.toMap()
        )
    }

    fun searchInstruments(query: String, types: List<String>?, limit: Int): SearchResponseDto {
        val start = System.currentTimeMillis()
        val rows = detailRepository.searchInstruments(query, types, limit.coerceIn(1, 50))
        val elapsed = System.currentTimeMillis() - start

        val results = rows.map { row ->
            SearchResultDto(
                id = (row["id"] as Number).toLong(),
                type = row["instrument_type"] as String,
                ticker = row["ticker"] as String,
                name = row["name"] as String,
                exchange = row["country"] as? String,
                matchType = row["match_type"] as String
            )
        }

        return SearchResponseDto(
            data = results,
            meta = SearchMetaDto(query = query, resultCount = results.size, searchTimeMs = elapsed)
        )
    }

    fun getDistinctValues(field: String, instrumentType: String): List<String> {
        return detailRepository.getDistinctValues(field, instrumentType)
    }

    fun getTypeCounts(): Map<String, Long> {
        return detailRepository.countByType()
    }

    private fun parsePayload(value: Any?): JsonNode? {
        if (value == null) return null
        return when (value) {
            is String -> objectMapper.readTree(value)
            else -> objectMapper.readTree(value.toString())
        }
    }

    private fun JsonNode.toMap(): Map<String, Any?> {
        return objectMapper.convertValue(this, Map::class.java) as Map<String, Any?>
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `docker compose exec backend ./gradlew compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/service/InstrumentScreenerService.kt
git commit -m "feat: add InstrumentScreenerService for filtering, detail, and search"
```

---

### Task 6: Instrument Controller

**Files:**
- Create: `backend/src/main/kotlin/com/portfolio/controller/InstrumentScreenerController.kt`

- [ ] **Step 1: Create unified controller for screener, detail, search, and reference data**

```kotlin
// InstrumentScreenerController.kt
package com.portfolio.controller

import com.portfolio.dto.request.InstrumentFilterRequest
import com.portfolio.dto.response.*
import com.portfolio.service.InstrumentScreenerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/screener")
class InstrumentScreenerController(
    private val service: InstrumentScreenerService
) {
    @GetMapping("/{type}")
    fun getInstruments(
        @PathVariable type: String,
        @RequestParam(required = false) tickerContains: String?,
        @RequestParam(required = false) nameContains: String?,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) exchange: String?,
        @RequestParam(required = false) sector: String?,
        @RequestParam(required = false) issuer: String?,
        @RequestParam(required = false) assetClass: String?,
        @RequestParam(required = false) fundCategory: String?,
        @RequestParam(required = false) fundStyle: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "ticker") sortField: String,
        @RequestParam(defaultValue = "asc") sortDirection: String
    ): ResponseEntity<PagedResponseDto<InstrumentScreenerDto>> {
        val instrumentType = mapTypeParam(type)
        val filter = InstrumentFilterRequest(
            instrumentType = instrumentType,
            tickerContains = tickerContains,
            nameContains = nameContains,
            country = country,
            exchange = exchange,
            sector = sector,
            issuer = issuer,
            assetClass = assetClass,
            fundCategory = fundCategory,
            fundStyle = fundStyle
        )
        val result = service.filterInstruments(filter, page, size, sortField, sortDirection)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/detail/{type}/{ticker}")
    fun getInstrumentDetail(
        @PathVariable type: String,
        @PathVariable ticker: String
    ): ResponseEntity<InstrumentDetailDto> {
        val instrumentType = mapTypeParam(type)
        val detail = service.getInstrumentDetail(ticker, instrumentType)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }

    @GetMapping("/search")
    fun searchInstruments(
        @RequestParam query: String,
        @RequestParam(required = false) types: List<String>?,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<SearchResponseDto> {
        val mappedTypes = types?.map { mapTypeParam(it) }
        val result = service.searchInstruments(query, mappedTypes, limit)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/reference/{type}/{field}")
    fun getReferenceData(
        @PathVariable type: String,
        @PathVariable field: String
    ): ResponseEntity<List<String>> {
        val instrumentType = mapTypeParam(type)
        val values = service.getDistinctValues(field, instrumentType)
        return ResponseEntity.ok(values)
    }

    @GetMapping("/counts")
    fun getTypeCounts(): ResponseEntity<Map<String, Long>> {
        return ResponseEntity.ok(service.getTypeCounts())
    }

    private fun mapTypeParam(type: String): String {
        return when (type.lowercase().replace("-", "_")) {
            "stocks", "stock" -> "STOCK"
            "etfs", "etf" -> "ETF"
            "mutual-funds", "mutual_funds", "mutualfunds" -> "MUTUAL_FUND"
            "preferred-stocks", "preferred_stocks" -> "PREFERRED_STOCK"
            "indices", "index" -> "INDEX"
            "bonds", "bond" -> "BOND"
            else -> type.uppercase()
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `docker compose exec backend ./gradlew compileKotlin`

- [ ] **Step 3: Test endpoints manually**

Run: `docker compose up --build -d backend`

Then test:
```bash
curl http://localhost:8080/api/v1/screener/stocks?size=3
curl http://localhost:8080/api/v1/screener/etfs?size=3
curl http://localhost:8080/api/v1/screener/mutual-funds?size=3
curl http://localhost:8080/api/v1/screener/detail/stock/AAPL
curl http://localhost:8080/api/v1/screener/search?query=apple
curl http://localhost:8080/api/v1/screener/reference/stocks/sector
curl http://localhost:8080/api/v1/screener/counts
```

Expected: JSON responses with real data from ingestion schema.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/controller/InstrumentScreenerController.kt
git commit -m "feat: add InstrumentScreenerController with screener, detail, search, and reference endpoints"
```

---

### Task 7: Add missing Search DTOs

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/dto/response/InstrumentDto.kt` (or create new file if DTOs don't exist)

- [ ] **Step 1: Ensure SearchResultDto and SearchResponseDto exist**

Check if `SearchResultDto` and `SearchResponseDto` already exist in the codebase. If they do, reuse them. If not, create:

```kotlin
// Add to existing DTO file or create SearchDto.kt
package com.portfolio.dto.response

data class SearchResultDto(
    val id: Long,
    val type: String,
    val ticker: String,
    val name: String,
    val exchange: String?,
    val matchType: String
)

data class SearchMetaDto(
    val query: String,
    val resultCount: Int,
    val searchTimeMs: Long
)

data class SearchResponseDto(
    val data: List<SearchResultDto>,
    val meta: SearchMetaDto
)
```

- [ ] **Step 2: Verify full backend build**

Run: `docker compose exec backend ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/dto/response/
git commit -m "feat: add search response DTOs for instrument search"
```

---

## Phase 2: Frontend — Screener & Detail Pages

### Task 8: Frontend Types and API Service

**Files:**
- Create: `frontend/src/types/screener.ts`
- Create: `frontend/src/services/screenerService.ts`

- [ ] **Step 1: Create TypeScript interfaces matching backend DTOs**

```typescript
// frontend/src/types/screener.ts

export type InstrumentType = 'STOCK' | 'ETF' | 'MUTUAL_FUND' | 'PREFERRED_STOCK' | 'INDEX' | 'BOND';

export interface InstrumentScreenerItem {
  id: number;
  ticker: string;
  name: string;
  instrumentType: InstrumentType;
  isin: string | null;
  currency: string | null;
  country: string | null;
  exchange: string | null;
  // Stock
  sector: string | null;
  marketCap: number | null;
  pe: number | null;
  eps: number | null;
  dividendYield: number | null;
  weekHigh52: number | null;
  weekLow52: number | null;
  beta: number | null;
  // ETF
  issuer: string | null;
  assetClass: string | null;
  expenseRatio: number | null;
  yield: number | null;
  totalAssets: number | null;
  holdingsCount: number | null;
  return1Y: number | null;
  // Mutual fund
  fundCategory: string | null;
  fundStyle: string | null;
  nav: number | null;
}

export interface InstrumentDetail {
  id: number;
  ticker: string;
  name: string;
  instrumentType: InstrumentType;
  isin: string | null;
  currency: string | null;
  country: string | null;
  general: Record<string, any> | null;
  highlights: Record<string, any> | null;
  valuation: Record<string, any> | null;
  technicals: Record<string, any> | null;
  financials: Record<string, any> | null;
  earnings: Record<string, any> | null;
  splitsDividends: Record<string, any> | null;
  sharesStats: Record<string, any> | null;
  analystRatings: Record<string, any> | null;
  etfData: Record<string, any> | null;
  mutualFundData: Record<string, any> | null;
}

export interface ScreenerFilter {
  tickerContains?: string;
  nameContains?: string;
  country?: string;
  exchange?: string;
  sector?: string;
  issuer?: string;
  assetClass?: string;
  fundCategory?: string;
  fundStyle?: string;
}

export interface PagedResponse<T> {
  data: T[];
  meta: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface SearchResult {
  id: number;
  type: InstrumentType;
  ticker: string;
  name: string;
  exchange: string | null;
  matchType: string;
}

export interface SearchResponse {
  data: SearchResult[];
  meta: { query: string; resultCount: number; searchTimeMs: number };
}

export interface TypeCounts {
  [key: string]: number;
}

export const INSTRUMENT_TYPE_CONFIG: Record<string, {
  label: string;
  pluralLabel: string;
  route: string;
  detailRoute: string;
}> = {
  STOCK: { label: 'Stock', pluralLabel: 'Stocks', route: 'stocks', detailRoute: 'stock' },
  ETF: { label: 'ETF', pluralLabel: 'ETFs', route: 'etfs', detailRoute: 'etf' },
  MUTUAL_FUND: { label: 'Mutual Fund', pluralLabel: 'Mutual Funds', route: 'mutual-funds', detailRoute: 'mutual-fund' },
  PREFERRED_STOCK: { label: 'Preferred Stock', pluralLabel: 'Preferred Stocks', route: 'preferred-stocks', detailRoute: 'preferred-stock' },
  INDEX: { label: 'Index', pluralLabel: 'Indices', route: 'indices', detailRoute: 'index' },
  BOND: { label: 'Bond', pluralLabel: 'Bonds', route: 'bonds', detailRoute: 'bond' },
};
```

- [ ] **Step 2: Create screener API service**

```typescript
// frontend/src/services/screenerService.ts

import { apiFetch } from './api';
import type {
  InstrumentScreenerItem,
  InstrumentDetail,
  ScreenerFilter,
  PagedResponse,
  SearchResponse,
  TypeCounts,
} from '@/types/screener';

export async function getScreenerInstruments(
  type: string,
  filter: ScreenerFilter,
  page: number,
  size: number,
  sortField: string = 'ticker',
  sortDirection: string = 'asc'
): Promise<PagedResponse<InstrumentScreenerItem>> {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  params.set('sortField', sortField);
  params.set('sortDirection', sortDirection);

  if (filter.tickerContains) params.set('tickerContains', filter.tickerContains);
  if (filter.nameContains) params.set('nameContains', filter.nameContains);
  if (filter.country) params.set('country', filter.country);
  if (filter.sector) params.set('sector', filter.sector);
  if (filter.issuer) params.set('issuer', filter.issuer);
  if (filter.assetClass) params.set('assetClass', filter.assetClass);
  if (filter.fundCategory) params.set('fundCategory', filter.fundCategory);
  if (filter.fundStyle) params.set('fundStyle', filter.fundStyle);

  return apiFetch(`/api/v1/screener/${type}?${params.toString()}`);
}

export async function getInstrumentDetail(
  type: string,
  ticker: string
): Promise<InstrumentDetail> {
  return apiFetch(`/api/v1/screener/detail/${type}/${ticker}`);
}

export async function searchInstruments(
  query: string,
  types?: string[],
  limit: number = 20
): Promise<SearchResponse> {
  const params = new URLSearchParams();
  params.set('query', query);
  params.set('limit', String(limit));
  if (types?.length) types.forEach(t => params.append('types', t));
  return apiFetch(`/api/v1/screener/search?${params.toString()}`);
}

export async function getReferenceData(
  type: string,
  field: string
): Promise<string[]> {
  return apiFetch(`/api/v1/screener/reference/${type}/${field}`);
}

export async function getTypeCounts(): Promise<TypeCounts> {
  return apiFetch('/api/v1/screener/counts');
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/screener.ts frontend/src/services/screenerService.ts
git commit -m "feat: add screener types and API service for all instrument types"
```

---

### Task 9: React Query Hooks

**Files:**
- Create: `frontend/src/hooks/useScreener.ts` (replace existing or create new)

- [ ] **Step 1: Create hooks for screener, detail, reference data, and search**

```typescript
// frontend/src/hooks/useScreener.ts

import { useQuery } from '@tanstack/react-query';
import {
  getScreenerInstruments,
  getInstrumentDetail,
  getReferenceData,
  getTypeCounts,
  searchInstruments,
} from '@/services/screenerService';
import type { ScreenerFilter } from '@/types/screener';

export function useInstrumentScreener(
  type: string,
  filter: ScreenerFilter,
  page: number,
  size: number,
  sortField: string = 'ticker',
  sortDirection: string = 'asc'
) {
  return useQuery({
    queryKey: ['screener', type, filter, page, size, sortField, sortDirection],
    queryFn: () => getScreenerInstruments(type, filter, page, size, sortField, sortDirection),
    staleTime: 2 * 60 * 1000,
  });
}

export function useInstrumentDetail(type: string, ticker: string) {
  return useQuery({
    queryKey: ['instrument-detail', type, ticker],
    queryFn: () => getInstrumentDetail(type, ticker),
    enabled: !!ticker && !!type,
    staleTime: 5 * 60 * 1000,
  });
}

export function useReferenceData(type: string, field: string) {
  return useQuery({
    queryKey: ['reference-data', type, field],
    queryFn: () => getReferenceData(type, field),
    staleTime: 10 * 60 * 1000,
  });
}

export function useTypeCounts() {
  return useQuery({
    queryKey: ['type-counts'],
    queryFn: getTypeCounts,
    staleTime: 5 * 60 * 1000,
  });
}

export function useInstrumentSearch(query: string, types?: string[], limit?: number) {
  return useQuery({
    queryKey: ['instrument-search', query, types, limit],
    queryFn: () => searchInstruments(query, types, limit),
    enabled: query.length >= 1,
    staleTime: 5 * 60 * 1000,
    placeholderData: (prev) => prev,
  });
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/hooks/useScreener.ts
git commit -m "feat: add React Query hooks for screener, detail, search, and reference data"
```

---

### Task 10: Screener Page Component

**Files:**
- Create: `frontend/src/pages/ScreenerPage.tsx`
- Create: `frontend/src/pages/ScreenerPage.css`
- Create: `frontend/src/components/screener/ScreenerSidebar.tsx`
- Create: `frontend/src/components/screener/ScreenerSidebar.css`

- [ ] **Step 1: Build ScreenerSidebar — collapsible type navigation with counts**

Follows the existing sidebar collapse pattern from the app layout. Shows instrument types with counts from `useTypeCounts`. Highlights active type based on current route. Collapses to icon-only when the app sidebar is collapsed (read from `sidebarStore`).

Refer to design mockup: `.superpowers/brainstorm/19683-1775999357/content/screener-hybrid.html`

- [ ] **Step 2: Build ScreenerPage — shared shell with type-specific column defs and filters**

Uses `useParams()` to get type from route. Renders ScreenerSidebar, type-specific filter dropdowns (using `useReferenceData`), search input, AG Grid with type-specific column definitions, and pagination.

Column definitions per type defined in a config object following the spec:
- STOCK: Ticker, Name, Sector, Country, Market Cap, P/E, EPS, Dividend Yield, 52wk Range, Beta
- ETF: Ticker, Name, Issuer, Asset Class, Expense Ratio, Yield, Total Assets, Holdings Count, 1Y Return
- MUTUAL_FUND: Ticker, Name, Fund Category, Fund Style, Expense Ratio, Yield, NAV, 1Y Return
- Sparse types: Ticker, Name, Exchange, Currency, Country

Row click navigates to `/instruments/{type}/{ticker}`.

- [ ] **Step 3: Add CSS for screener page and sidebar**

Follow existing CSS patterns — companion `.css` files, CSS custom properties for theming.

- [ ] **Step 4: Test in browser**

Run: `npm run dev` (from frontend/)
Navigate to `http://localhost:3000/screener/stocks`
Verify: Sidebar shows all 6 types with counts, filters work, AG Grid shows stock data, pagination works, clicking a row navigates to detail.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/ScreenerPage.tsx frontend/src/pages/ScreenerPage.css
git add frontend/src/components/screener/ScreenerSidebar.tsx frontend/src/components/screener/ScreenerSidebar.css
git commit -m "feat: add unified ScreenerPage with collapsible sidebar and type-specific columns"
```

---

### Task 11: Instrument Detail Page Shell

**Files:**
- Create: `frontend/src/pages/InstrumentDetailPage.tsx`
- Create: `frontend/src/pages/InstrumentDetailPage.css`

- [ ] **Step 1: Build shared detail page shell**

Uses `useParams()` to get type and ticker from route. Fetches data via `useInstrumentDetail`. Renders:
- Breadcrumb (Screener > Type > Ticker)
- Header (name, ticker badge, type badge, subtitle with classification)
- Hero metrics (6 cards — type-specific content)
- Section nav (type-specific sections)
- Conditionally loads type-specific section components

Refer to mockups for layout: `detail-stock-final.html`, `detail-etf-final-v3.html`, `detail-mutualfund-v3.html`

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/InstrumentDetailPage.tsx frontend/src/pages/InstrumentDetailPage.css
git commit -m "feat: add InstrumentDetailPage shell with hero metrics and section nav"
```

---

### Task 12: Stock Detail Sections (Charts)

**Files:**
- Create: `frontend/src/components/instruments/StockDetailSections.tsx`
- Create: `frontend/src/components/instruments/StockDetailSections.css`

- [ ] **Step 1: Build stock-specific sections using AG Charts**

Implement each section from the stock detail mockup (`detail-stock-final.html`):

1. **About section** — description, CEO, employees, IPO date from `general`
2. **Financials** — Stacked bar chart (AG Charts) for revenue composition + YoY change table with Income/Balance Sheet/Cash Flow tabs
3. **Valuation** — Horizontal bar chart for multiples + Margin sparklines (AG Charts line series)
4. **Technicals** — Key metrics + 52-week range visualization with MA markers
5. **Dividends & Ownership** — Dividend history bar chart + Ownership donut (AG Charts pie series)
6. **Analyst Ratings** — Conditional section, AG Charts horizontal bar for Strong Buy/Buy/Hold/Sell/Strong Sell

All charts: enable `tooltip.enabled: true` in AG Charts config for hover data display.

- [ ] **Step 2: Test in browser**

Navigate to `/instruments/stock/AAPL` (or any stock with data).
Verify all sections render with real data. Hover over charts to check tooltips.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/instruments/StockDetailSections.tsx frontend/src/components/instruments/StockDetailSections.css
git commit -m "feat: add StockDetailSections with financial charts and visualizations"
```

---

### Task 13: ETF Detail Sections (Charts)

**Files:**
- Create: `frontend/src/components/instruments/EtfDetailSections.tsx`
- Create: `frontend/src/components/instruments/EtfDetailSections.css`

- [ ] **Step 1: Build ETF-specific sections using AG Charts**

Implement each section from the ETF detail mockup (`detail-etf-final-v3.html`):

1. **Performance** — Returns bar chart (YTD, 1Y, 3Y, 5Y, 10Y) + Risk profile cards with gauge bars
2. **Top Holdings** — Horizontal bar chart for top 10 holdings + Market cap donut
3. **Sector & Geographic** — Sector weights donut + Geographic exposure donut (regions only, no countries for ETFs)
4. **Valuation & Asset Allocation** — Butterfly table (Portfolio vs Category with mirror bars) + Asset allocation stacked bar + Growth rates

- [ ] **Step 2: Test in browser**

Navigate to `/instruments/etf/VTI`
Verify all sections render. Check hover tooltips.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/instruments/EtfDetailSections.tsx frontend/src/components/instruments/EtfDetailSections.css
git commit -m "feat: add EtfDetailSections with performance, holdings, and sector charts"
```

---

### Task 14: Mutual Fund Detail Sections (Charts)

**Files:**
- Create: `frontend/src/components/instruments/MutualFundDetailSections.tsx`
- Create: `frontend/src/components/instruments/MutualFundDetailSections.css`

- [ ] **Step 1: Build mutual fund-specific sections using AG Charts**

Implement each section from the mutual fund detail mockup (`detail-mutualfund-v3.html`):

1. **Performance** — Returns bar chart (1Y, 3Y, 5Y) + Fund profile cards
2. **Top Holdings** — Horizontal bar chart + Market cap donut
3. **Sector & Geographic** — Sector weights donut + Concentric geographic chart (regions inner pie + countries outer donut)
4. **Valuation & Asset Allocation** — Butterfly table (Portfolio vs Benchmark with category average reference row) + Asset allocation stacked bar + Growth rates

- [ ] **Step 2: Test in browser**

Navigate to `/instruments/mutual-fund/CGHIX`
Verify all sections render. Check concentric geographic chart especially.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/instruments/MutualFundDetailSections.tsx frontend/src/components/instruments/MutualFundDetailSections.css
git commit -m "feat: add MutualFundDetailSections with geographic concentric chart"
```

---

### Task 15: Sparse Type Detail Sections + Routes

**Files:**
- Create: `frontend/src/components/instruments/BasicDetailSections.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Build basic detail sections for PREFERRED_STOCK, INDEX, BOND**

Shows General info and Technicals if available. Minimal layout.

- [ ] **Step 2: Add all routes to App.tsx**

```typescript
// New routes in App.tsx (replace old screener/detail routes)
<Route path="/screener/:type" element={<ScreenerPage />} />
<Route path="/instruments/:type/:ticker" element={<InstrumentDetailPage />} />
```

Remove old routes:
```typescript
// DELETE these
<Route path="/screener/stocks" element={<StockScreenerPage />} />
<Route path="/screener/etfs" element={<EtfScreenerPage />} />
<Route path="/stocks/:ticker" element={<StockDetailPage />} />
<Route path="/etfs/:symbol" element={<EtfDetailPage />} />
```

- [ ] **Step 3: Update sidebar navigation links**

Update the app sidebar to point to new screener routes. Add "Screener" section with sub-links or a single "Screener" link that defaults to `/screener/stocks`.

- [ ] **Step 4: Test all routes in browser**

Navigate to each type's screener and detail page. Verify sparse types show basic layout gracefully.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/instruments/BasicDetailSections.tsx
git add frontend/src/App.tsx
git commit -m "feat: add sparse type detail sections and wire up all routes"
```

---

### Task 16: Update Instrument Search Autocomplete

**Files:**
- Modify: `frontend/src/components/instruments/InstrumentSearchAutocomplete.tsx`

- [ ] **Step 1: Update to use new search endpoint and types**

Change the autocomplete to call `searchInstruments` from `screenerService.ts` instead of the old `instrumentService.ts`. Update type badges to show all 6 instrument types. Update click handler to navigate to `/instruments/{type}/{ticker}`.

- [ ] **Step 2: Test autocomplete in browser**

Type "apple" in search — should show AAPL as a Stock result. Type "VTI" — should show as ETF. Clicking should navigate to correct detail page.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/instruments/InstrumentSearchAutocomplete.tsx
git commit -m "feat: update InstrumentSearchAutocomplete to use new screener endpoints"
```

---

## Phase 3: Legacy Removal & Dependency Migration

### Task 17: Migrate BrokerPosition References

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/entity/BrokerPosition.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt`
- Modify: Related services that reference `Stock` entity through positions

- [ ] **Step 1: Update BrokerPosition entity**

Replace the `@ManyToOne` FK to `Stock` with a `ticker` + `exchange` string field pair. Update the Flyway migration to add new columns and drop the FK.

- [ ] **Step 2: Update PositionFetchService**

When mapping SnapTrade positions to instruments, look up via `ingestion.instruments` by ticker instead of `stockRepository.findByTicker()`.

- [ ] **Step 3: Update DashboardDataService and sub-services**

Replace all `position.stock.xxx` references with either the stored ticker/name on BrokerPosition or a cross-schema lookup.

- [ ] **Step 4: Update LookThroughService**

Replace `etfHoldingRepository` usage with JSONB queries on `ETF_Data.Holdings` from `provider_raw_data`.

- [ ] **Step 5: Update remaining dependent services**

DriftCalculationService, BenchmarkService, ActivityIngestionService, ModelPortfolioService — replace all Stock/Etf entity references.

- [ ] **Step 6: Verify all tests pass**

Run: `docker compose exec backend ./gradlew test`

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: migrate broker and portfolio services from Stock/Etf entities to ingestion schema"
```

---

### Task 18: Flyway Migration — Drop Old Tables

**Files:**
- Create: `backend/src/main/resources/db/migration/V67__drop_legacy_screener_tables.sql`

- [ ] **Step 1: Write migration to drop old tables**

Check the highest existing migration number and increment by 1. Drop tables in correct order (FK dependencies first):

```sql
-- V67__drop_legacy_screener_tables.sql

-- Drop FK-dependent tables first
DROP TABLE IF EXISTS etf_holdings CASCADE;
DROP TABLE IF EXISTS etf_sector_allocations_factset CASCADE;

-- Drop main instrument tables
DROP TABLE IF EXISTS stocks CASCADE;
DROP TABLE IF EXISTS etfs CASCADE;

-- Drop GICS reference tables
DROP TABLE IF EXISTS gics_sub_industry_aliases CASCADE;
DROP TABLE IF EXISTS gics_sector_aliases CASCADE;
DROP TABLE IF EXISTS gics_sub_industries CASCADE;
DROP TABLE IF EXISTS gics_industries CASCADE;
DROP TABLE IF EXISTS gics_industry_groups CASCADE;
DROP TABLE IF EXISTS gics_sectors CASCADE;

-- Drop geographic reference tables
DROP TABLE IF EXISTS countries CASCADE;
DROP TABLE IF EXISTS regions CASCADE;

-- Drop legacy ingestion tracking
DROP TABLE IF EXISTS ingestion_errors CASCADE;
DROP TABLE IF EXISTS ingestion_steps CASCADE;
DROP TABLE IF EXISTS ingestion_runs CASCADE;
DROP TABLE IF EXISTS ingestion_batches CASCADE;
DROP TABLE IF EXISTS data_sources CASCADE;
```

- [ ] **Step 2: Test migration runs cleanly**

Run: `docker compose down && docker compose up --build -d`
Check: `docker compose logs backend | grep -i flyway`
Expected: Migration V67 applied successfully.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V67__drop_legacy_screener_tables.sql
git commit -m "feat: add Flyway migration to drop legacy screener tables"
```

---

### Task 19: Remove Old Backend Code

**Files to delete:**
- `backend/src/main/kotlin/com/portfolio/entity/Stock.kt`
- `backend/src/main/kotlin/com/portfolio/entity/Etf.kt`
- `backend/src/main/kotlin/com/portfolio/entity/EtfHolding.kt`
- `backend/src/main/kotlin/com/portfolio/repository/StockRepository.kt`
- `backend/src/main/kotlin/com/portfolio/repository/EtfRepository.kt`
- `backend/src/main/kotlin/com/portfolio/repository/EtfHoldingRepository.kt`
- `backend/src/main/kotlin/com/portfolio/controller/StockController.kt`
- `backend/src/main/kotlin/com/portfolio/controller/EtfController.kt`
- `backend/src/main/kotlin/com/portfolio/service/ScreenerService.kt`
- `backend/src/main/kotlin/com/portfolio/service/HoldingsService.kt`
- `backend/src/main/kotlin/com/portfolio/service/InstrumentSearchService.kt`
- `backend/src/main/kotlin/com/portfolio/ingestion/` (entire legacy ingestion package — client, scheduler, service, controller, DTOs)
- GICS entity classes, country/region entities, related repositories
- Old reference data service methods that query dropped tables

- [ ] **Step 1: Delete old entity and repository files**
- [ ] **Step 2: Delete old controllers**
- [ ] **Step 3: Delete old services**
- [ ] **Step 4: Delete legacy ingestion code**
- [ ] **Step 5: Update ReferenceDataService to use new ingestion queries**
- [ ] **Step 6: Fix all compilation errors from removed references**
- [ ] **Step 7: Verify full build passes**

Run: `docker compose exec backend ./gradlew build`

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: remove legacy Stock/Etf entities, controllers, services, and ingestion code"
```

---

### Task 20: Remove Old Frontend Code

**Files to delete:**
- `frontend/src/pages/StockScreenerPage.tsx`
- `frontend/src/pages/EtfScreenerPage.tsx`
- `frontend/src/pages/StockDetailPage.tsx`
- `frontend/src/pages/EtfDetailPage.tsx`
- `frontend/src/types/instrument.ts` (old Stock/Etf interfaces — check if still used by portfolioStore)
- Old service functions from `frontend/src/services/instrumentService.ts` (getStocks, getEtfs, getEtfHoldings)
- Old hooks from `frontend/src/hooks/useScreener.ts` (useStockScreener, useEtfScreener) — if separate file from new hooks
- Old hooks from `frontend/src/hooks/useReferenceData.ts` (useGicsSectors, useCountries, useEtfIssuers, useEtfAssetClasses)

- [ ] **Step 1: Delete old page components and their CSS files**
- [ ] **Step 2: Delete old service functions (keep apiFetch)**
- [ ] **Step 3: Delete old hooks (keep new ones)**
- [ ] **Step 4: Update portfolioStore if it references old types**
- [ ] **Step 5: Verify frontend build passes**

Run: `npm run build` (from frontend/)

- [ ] **Step 6: Run lint and tests**

Run: `npm run lint && npm run test:run` (from frontend/)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: remove old screener pages, services, hooks, and types"
```

---

### Task 21: Update Documentation

**Files:**
- Modify: `docs/agent-reference/api-endpoints.md`
- Modify: `docs/agent-reference/backend-services.md`
- Modify: `docs/agent-reference/frontend-map.md`
- Modify: `docs/agent-reference/database-schema.md`
- Modify: `docs/agent-reference/entity-relationships.md`
- Modify: `docs/agent-reference/unused-legacy.md`

- [ ] **Step 1: Update API endpoints documentation**

Add new `/api/v1/screener/*` endpoints. Remove old `/api/v1/stocks`, `/api/v1/etfs` endpoints.

- [ ] **Step 2: Update backend services documentation**

Document InstrumentScreenerService, InstrumentScreenerRepository, InstrumentDetailRepository. Remove old ScreenerService, HoldingsService, etc.

- [ ] **Step 3: Update frontend map**

Document new ScreenerPage, InstrumentDetailPage, chart components. Remove old pages.

- [ ] **Step 4: Update database schema**

Remove dropped tables. Document cross-schema query pattern.

- [ ] **Step 5: Update entity relationships**

Document new instrument lookup pattern (ticker + cross-schema). Remove old Stock/Etf entity docs.

- [ ] **Step 6: Update unused-legacy**

Mark removed code as cleaned up. Note any remaining technical debt.

- [ ] **Step 7: Commit**

```bash
git add docs/
git commit -m "docs: update agent-reference documentation for screener migration"
```

---

## Verification Checklist

- [ ] `docker compose up --build` — full stack starts without errors
- [ ] Flyway migration V67 runs cleanly
- [ ] `docker compose exec backend ./gradlew test` — all tests pass
- [ ] `npm run build` — frontend production build succeeds
- [ ] `npm run lint` — no lint errors
- [ ] `npm run test:run` — all frontend tests pass
- [ ] Navigate to `/screener/stocks` — grid loads with 16K+ instruments
- [ ] Navigate to `/screener/etfs` — grid loads with ETF-specific columns
- [ ] Navigate to `/screener/mutual-funds` — grid loads with MF columns
- [ ] Navigate to `/screener/bonds` — shows basic columns
- [ ] Filter by sector on stocks screener — results narrow correctly
- [ ] Click stock row → detail page with charts renders
- [ ] Click ETF row → detail page with holdings/sector charts renders
- [ ] Click mutual fund row → detail page with concentric geographic chart renders
- [ ] Hover over all chart types — tooltips display data
- [ ] Sidebar collapses/expands with type counts
- [ ] Search autocomplete finds instruments across all types
- [ ] "Add to Portfolio" button works from screener and detail pages
- [ ] Dashboard widgets still function
- [ ] Broker connections and position syncing still work
