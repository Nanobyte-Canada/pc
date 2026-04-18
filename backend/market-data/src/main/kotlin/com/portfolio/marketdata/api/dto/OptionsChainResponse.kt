package com.portfolio.marketdata.api.dto

import com.portfolio.common.domain.Greeks
import com.portfolio.common.domain.OptionQuote
import com.portfolio.common.domain.OptionType
import com.portfolio.common.domain.OptionsChain
import com.portfolio.common.domain.StrikeData
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class OptionQuoteDto(
    val underlying: String,
    val optionType: OptionType,
    val strike: BigDecimal,
    val expiry: LocalDate,
    val bid: BigDecimal,
    val ask: BigDecimal,
    val last: BigDecimal,
    val mid: BigDecimal,
    val spread: BigDecimal,
    val spreadQuality: BigDecimal,
    val volume: Long,
    val openInterest: Long,
    val greeks: Greeks?,
    val timestamp: Instant
) {
    companion object {
        fun fromDomain(quote: OptionQuote): OptionQuoteDto = OptionQuoteDto(
            underlying = quote.underlying, optionType = quote.optionType, strike = quote.strike,
            expiry = quote.expiry, bid = quote.bid, ask = quote.ask, last = quote.last,
            mid = quote.mid, spread = quote.spread, spreadQuality = quote.spreadQuality,
            volume = quote.volume, openInterest = quote.openInterest, greeks = quote.greeks,
            timestamp = quote.timestamp
        )
    }
}

data class StrikeDataDto(
    val call: OptionQuoteDto?,
    val put: OptionQuoteDto?
) {
    companion object {
        fun fromDomain(strikeData: StrikeData): StrikeDataDto = StrikeDataDto(
            call = strikeData.call?.let { OptionQuoteDto.fromDomain(it) },
            put = strikeData.put?.let { OptionQuoteDto.fromDomain(it) }
        )
    }
}

data class OptionsChainResponse(
    val underlying: String,
    val spotPrice: BigDecimal,
    val expirations: Map<LocalDate, Map<BigDecimal, StrikeDataDto>>
) {
    companion object {
        fun fromDomain(chain: OptionsChain): OptionsChainResponse = OptionsChainResponse(
            underlying = chain.underlying,
            spotPrice = chain.spotPrice,
            expirations = chain.expirations.mapValues { (_, strikes) ->
                strikes.mapValues { (_, strikeData) -> StrikeDataDto.fromDomain(strikeData) }
            }
        )
    }
}
