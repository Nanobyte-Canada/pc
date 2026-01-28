package com.portfolio.dto.response

import com.portfolio.entity.Etf
import com.portfolio.entity.MutualFund
import com.portfolio.entity.Stock

enum class InstrumentType {
    STOCK, ETF, MUTUAL_FUND
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
    val exchange: String,
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
        fun from(stock: Stock): StockDto {
            val sector = stock.gicsSubIndustry?.industry?.industryGroup?.sector
            return StockDto(
                id = stock.id,
                ticker = stock.ticker,
                exchange = stock.exchange,
                name = stock.name,
                isin = stock.isin,
                cusip = stock.cusip,
                sedol = stock.sedol,
                currency = stock.currency,
                country = stock.country,
                sector = sector?.let { SectorDto(it.code, it.name) },
                status = stock.status.name
            )
        }
    }
}

data class EtfDto(
    val id: Long,
    val symbol: String,
    val exchange: String,
    val name: String,
    val isin: String?,
    val cusip: String?,
    val issuer: String?,
    val currency: String,
    val domicile: String,
    val inceptionDate: String?,
    val expenseRatio: Double?,
    val assetClass: String?,
    val status: String
) {
    companion object {
        fun from(etf: Etf): EtfDto {
            return EtfDto(
                id = etf.id,
                symbol = etf.symbol,
                exchange = etf.exchange,
                name = etf.name,
                isin = etf.isin,
                cusip = etf.cusip,
                issuer = etf.issuer,
                currency = etf.currency,
                domicile = etf.domicile,
                inceptionDate = etf.inceptionDate?.toString(),
                expenseRatio = etf.expenseRatio?.toDouble(),
                assetClass = etf.assetClass,
                status = etf.status.name
            )
        }
    }
}

data class MutualFundDto(
    val id: Long,
    val symbol: String,
    val name: String,
    val isin: String?,
    val cusip: String?,
    val issuer: String?,
    val currency: String,
    val domicile: String,
    val inceptionDate: String?,
    val expenseRatio: Double?,
    val fundType: String?,
    val assetClass: String?,
    val minimumInvestment: Double?,
    val status: String
) {
    companion object {
        fun from(fund: MutualFund): MutualFundDto {
            return MutualFundDto(
                id = fund.id,
                symbol = fund.symbol,
                name = fund.name,
                isin = fund.isin,
                cusip = fund.cusip,
                issuer = fund.issuer,
                currency = fund.currency,
                domicile = fund.domicile,
                inceptionDate = fund.inceptionDate?.toString(),
                expenseRatio = fund.expenseRatio?.toDouble(),
                fundType = fund.fundType,
                assetClass = fund.assetClass,
                minimumInvestment = fund.minimumInvestment?.toDouble(),
                status = fund.status.name
            )
        }
    }
}
