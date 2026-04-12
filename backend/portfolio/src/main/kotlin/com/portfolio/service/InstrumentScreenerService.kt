package com.portfolio.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.dto.request.InstrumentFilterRequest
import com.portfolio.dto.response.InstrumentDetailDto
import com.portfolio.dto.response.InstrumentScreenerDto
import com.portfolio.dto.response.PageMetaDto
import com.portfolio.dto.response.PagedResponseDto
import com.portfolio.dto.response.ScreenerSearchMetaDto
import com.portfolio.dto.response.ScreenerSearchResponseDto
import com.portfolio.dto.response.ScreenerSearchResultDto
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
        val totalElements = screenerRepository.countInstruments(filter)
        val totalPages = if (totalElements == 0L) 0 else ((totalElements + clampedSize - 1) / clampedSize).toInt()

        val data = rows.map { row -> InstrumentScreenerDto.fromRow(row, filter.instrumentType) }

        return PagedResponseDto(
            data = data,
            meta = PageMetaDto(
                page = page,
                size = clampedSize,
                totalElements = totalElements,
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
            instrumentType = row["instrument_type"] as String,
            isin = row["isin"] as? String,
            currency = row["currency"] as? String,
            country = row["country"] as? String,
            general = payload?.path("General")?.takeUnless { it.isMissingNode }?.toMap(),
            highlights = payload?.path("Highlights")?.takeUnless { it.isMissingNode }?.toMap(),
            valuation = payload?.path("Valuation")?.takeUnless { it.isMissingNode }?.toMap(),
            technicals = payload?.path("Technicals")?.takeUnless { it.isMissingNode }?.toMap(),
            financials = payload?.path("Financials")?.takeUnless { it.isMissingNode }?.toMap(),
            earnings = payload?.path("Earnings")?.takeUnless { it.isMissingNode }?.toMap(),
            splitsDividends = payload?.path("SplitsDividends")?.takeUnless { it.isMissingNode }?.toMap(),
            sharesStats = payload?.path("SharesStats")?.takeUnless { it.isMissingNode }?.toMap(),
            analystRatings = payload?.path("AnalystRatings")?.takeUnless { it.isMissingNode }?.toMap(),
            etfData = payload?.path("ETF_Data")?.takeUnless { it.isMissingNode }?.toMap(),
            mutualFundData = payload?.path("MutualFund_Data")?.takeUnless { it.isMissingNode }?.toMap()
        )
    }

    fun searchInstruments(query: String, types: List<String>?, limit: Int): ScreenerSearchResponseDto {
        val clampedLimit = limit.coerceIn(1, 50)
        val startTime = System.currentTimeMillis()

        val rows = detailRepository.searchInstruments(query, types, clampedLimit)

        val data = rows.map { row ->
            ScreenerSearchResultDto(
                id = (row["id"] as Number).toLong(),
                type = row["instrument_type"] as String,
                ticker = row["ticker"] as String,
                name = row["name"] as String,
                exchange = row["country"] as? String,
                matchType = row["match_type"] as String
            )
        }

        val searchTimeMs = System.currentTimeMillis() - startTime

        return ScreenerSearchResponseDto(
            data = data,
            meta = ScreenerSearchMetaDto(
                query = query,
                resultCount = data.size,
                searchTimeMs = searchTimeMs
            )
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

    @Suppress("UNCHECKED_CAST")
    private fun JsonNode.toMap(): Map<String, Any?> {
        return objectMapper.convertValue(this, Map::class.java) as Map<String, Any?>
    }
}
