package com.portfolio.service

import com.portfolio.dto.response.InstrumentType
import com.portfolio.dto.response.MatchType
import com.portfolio.dto.response.SearchResultDto
import com.portfolio.dto.response.SearchResponseDto
import com.portfolio.dto.response.SearchMetaDto
import com.portfolio.entity.Stock
import com.portfolio.entity.Etf
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.StockRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class InstrumentSearchService(
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository
) {
    fun search(
        query: String,
        types: Set<InstrumentType>,
        limit: Int = 10,
        includeInactive: Boolean = false
    ): SearchResponseDto {
        val startTime = System.currentTimeMillis()
        val normalizedQuery = query.uppercase().trim()

        if (normalizedQuery.isEmpty()) {
            return SearchResponseDto(
                data = emptyList(),
                meta = SearchMetaDto(query, 0, 0)
            )
        }

        val results = mutableListOf<SearchResultDto>()
        val supportedTypes = setOf(InstrumentType.STOCK, InstrumentType.ETF)
        val searchTypes = if (types.isEmpty()) supportedTypes else types.intersect(supportedTypes)

        if (isIsinFormat(normalizedQuery) || isCusipFormat(normalizedQuery) || isSedolFormat(normalizedQuery)) {
            searchByIdentifiers(normalizedQuery, searchTypes, includeInactive, results)
        }

        if (results.size < limit) {
            searchExactTicker(normalizedQuery, searchTypes, includeInactive, results)
        }

        val remaining = limit - results.size
        if (remaining > 0) {
            searchTickerPrefix(normalizedQuery, searchTypes, includeInactive, remaining, results)
        }

        val stillRemaining = limit - results.size
        if (stillRemaining > 0) {
            searchNameContains(normalizedQuery, searchTypes, includeInactive, stillRemaining, results)
        }

        val searchTimeMs = System.currentTimeMillis() - startTime
        val finalResults = results.take(limit)

        return SearchResponseDto(
            data = finalResults,
            meta = SearchMetaDto(
                query = query,
                resultCount = finalResults.size,
                searchTimeMs = searchTimeMs
            )
        )
    }

    private fun isIsinFormat(query: String): Boolean =
        query.length == 12 && query.take(2).all { it.isLetter() }

    private fun isCusipFormat(query: String): Boolean =
        query.length == 9 && query.all { it.isLetterOrDigit() }

    private fun isSedolFormat(query: String): Boolean =
        query.length == 7 && query.all { it.isLetterOrDigit() }

    private fun searchByIdentifiers(
        query: String,
        searchTypes: Set<InstrumentType>,
        includeInactive: Boolean,
        results: MutableList<SearchResultDto>
    ) {
        if (InstrumentType.STOCK in searchTypes) {
            val stockByIsin = if (includeInactive) stockRepository.findByIsin(query) else stockRepository.findByIsinAndIsActiveTrue(query)
            stockByIsin?.let { results.add(it.toSearchResult(MatchType.IDENTIFIER_EXACT)) }

            if (stockByIsin == null) {
                val stockByCusip = if (includeInactive) stockRepository.findByCusip(query) else stockRepository.findByCusipAndIsActiveTrue(query)
                stockByCusip?.let { results.add(it.toSearchResult(MatchType.IDENTIFIER_EXACT)) }
            }

            if (stockByIsin == null) {
                val stockBySedol = if (includeInactive) stockRepository.findBySedol(query) else stockRepository.findBySedolAndIsActiveTrue(query)
                stockBySedol?.let { results.add(it.toSearchResult(MatchType.IDENTIFIER_EXACT)) }
            }
        }

        if (InstrumentType.ETF in searchTypes) {
            val etfByIsin = if (includeInactive) etfRepository.findByIsin(query) else etfRepository.findByIsinAndIsActiveTrue(query)
            etfByIsin?.let { results.add(it.toSearchResult(MatchType.IDENTIFIER_EXACT)) }

            if (etfByIsin == null) {
                val etfByCusip = if (includeInactive) etfRepository.findByCusip(query) else etfRepository.findByCusipAndIsActiveTrue(query)
                etfByCusip?.let { results.add(it.toSearchResult(MatchType.IDENTIFIER_EXACT)) }
            }
        }
    }

    private fun searchExactTicker(
        query: String,
        searchTypes: Set<InstrumentType>,
        includeInactive: Boolean,
        results: MutableList<SearchResultDto>
    ) {
        if (InstrumentType.STOCK in searchTypes) {
            val stock = if (includeInactive) stockRepository.findByTickerIgnoreCase(query) else stockRepository.findByTickerIgnoreCaseAndIsActiveTrue(query)
            stock?.let {
                if (results.none { r -> r.id == "stock-${it.id}" }) {
                    results.add(it.toSearchResult(MatchType.TICKER_EXACT))
                }
            }
        }

        if (InstrumentType.ETF in searchTypes) {
            val etf = if (includeInactive) etfRepository.findBySymbolIgnoreCase(query) else etfRepository.findBySymbolIgnoreCaseAndIsActiveTrue(query)
            etf?.let {
                if (results.none { r -> r.id == "etf-${it.id}" }) {
                    results.add(it.toSearchResult(MatchType.TICKER_EXACT))
                }
            }
        }
    }

    private fun searchTickerPrefix(
        query: String,
        searchTypes: Set<InstrumentType>,
        includeInactive: Boolean,
        limit: Int,
        results: MutableList<SearchResultDto>
    ) {
        val pageable = PageRequest.of(0, limit)

        if (InstrumentType.STOCK in searchTypes) {
            val stocks = if (includeInactive) stockRepository.findByTickerStartingWithIgnoreCase(query, pageable)
                         else stockRepository.findByTickerStartingWithIgnoreCaseAndActive(query, pageable)
            stocks.filter { stock -> results.none { it.id == "stock-${stock.id}" } }
                .forEach { results.add(it.toSearchResult(MatchType.TICKER_PREFIX)) }
        }

        if (InstrumentType.ETF in searchTypes) {
            val etfs = if (includeInactive) etfRepository.findBySymbolStartingWithIgnoreCase(query, pageable)
                       else etfRepository.findBySymbolStartingWithIgnoreCaseAndActive(query, pageable)
            etfs.filter { etf -> results.none { it.id == "etf-${etf.id}" } }
                .forEach { results.add(it.toSearchResult(MatchType.TICKER_PREFIX)) }
        }
    }

    private fun searchNameContains(
        query: String,
        searchTypes: Set<InstrumentType>,
        includeInactive: Boolean,
        limit: Int,
        results: MutableList<SearchResultDto>
    ) {
        val pageable = PageRequest.of(0, limit)

        if (InstrumentType.STOCK in searchTypes) {
            val stocks = if (includeInactive) stockRepository.findByNameContainingIgnoreCase(query, pageable)
                         else stockRepository.findByNameContainingIgnoreCaseAndActive(query, pageable)
            stocks.filter { stock -> results.none { it.id == "stock-${stock.id}" } }
                .forEach { results.add(it.toSearchResult(MatchType.NAME_CONTAINS)) }
        }

        if (InstrumentType.ETF in searchTypes) {
            val etfs = if (includeInactive) etfRepository.findByNameContainingIgnoreCase(query, pageable)
                       else etfRepository.findByNameContainingIgnoreCaseAndActive(query, pageable)
            etfs.filter { etf -> results.none { it.id == "etf-${etf.id}" } }
                .forEach { results.add(it.toSearchResult(MatchType.NAME_CONTAINS)) }
        }
    }

    private fun Stock.toSearchResult(matchType: MatchType) = SearchResultDto(
        id = "stock-$id",
        type = InstrumentType.STOCK,
        ticker = ticker,
        name = name,
        exchange = exchangeCode,
        matchType = matchType,
        status = status.name,
        isActive = isActive
    )

    private fun Etf.toSearchResult(matchType: MatchType) = SearchResultDto(
        id = "etf-$id",
        type = InstrumentType.ETF,
        ticker = symbol,
        name = name,
        exchange = null,
        matchType = matchType,
        status = status.name,
        isActive = isActive
    )
}
