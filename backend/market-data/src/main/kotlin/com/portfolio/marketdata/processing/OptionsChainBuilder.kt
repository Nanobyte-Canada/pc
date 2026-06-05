package com.portfolio.marketdata.processing

import com.portfolio.common.domain.OptionQuote
import com.portfolio.common.domain.OptionType
import com.portfolio.common.domain.OptionsChain
import com.portfolio.common.domain.StrikeData
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class OptionsChainBuilder {

    fun build(underlying: String, spotPrice: BigDecimal, quotes: List<OptionQuote>): OptionsChain {
        val relevantQuotes = quotes.filter { it.underlying == underlying }

        val grouped = relevantQuotes.groupBy { it.expiry }
            .mapValues { (_, expiryQuotes) ->
                expiryQuotes.groupBy { it.strike }
                    .mapValues { (_, strikeQuotes) ->
                        StrikeData(
                            call = strikeQuotes.firstOrNull { it.optionType == OptionType.CALL },
                            put = strikeQuotes.firstOrNull { it.optionType == OptionType.PUT }
                        )
                    }
                    .toSortedMap()
            }
            .toSortedMap()

        return OptionsChain(underlying = underlying, spotPrice = spotPrice, expirations = grouped)
    }
}
