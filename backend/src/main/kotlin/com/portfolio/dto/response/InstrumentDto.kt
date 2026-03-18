package com.portfolio.dto.response

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.entity.Etf
import com.portfolio.entity.Stock

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

data class StockDetailDto(
    val id: Long,
    val ticker: String,
    val name: String,
    val currency: String,
    val country: String,
    val isin: String?,
    val avIngestionStatus: String,
    val avRawPayload: JsonNode?
) {
    companion object {
        fun from(stock: Stock) = StockDetailDto(
            id = stock.id,
            ticker = stock.ticker,
            name = stock.name,
            currency = stock.currency,
            country = stock.country,
            isin = stock.isin,
            avIngestionStatus = stock.avIngestionStatus.name,
            avRawPayload = stock.avRawPayload
        )
    }
}

data class EtfDetailDto(
    val id: Long,
    val symbol: String,
    val name: String,
    val issuer: String?,
    val assetClass: String?,
    val inceptionDate: String?,
    val etfcomEnrichmentStatus: String,
    val etfcomRawPayload: String?
) {
    companion object {
        fun from(etf: Etf) = EtfDetailDto(
            id = etf.id,
            symbol = etf.symbol,
            name = etf.name,
            issuer = etf.issuer,
            assetClass = etf.assetClass,
            inceptionDate = etf.inceptionDate?.toString(),
            etfcomEnrichmentStatus = etf.etfcomEnrichmentStatus.name,
            etfcomRawPayload = etf.etfcomRawPayload
        )
    }
}

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

