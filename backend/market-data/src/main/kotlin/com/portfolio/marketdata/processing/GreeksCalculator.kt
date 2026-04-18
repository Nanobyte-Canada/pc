package com.portfolio.marketdata.processing

import com.portfolio.common.domain.Greeks
import com.portfolio.common.domain.GreeksSource
import com.portfolio.common.domain.OptionType
import com.portfolio.common.math.BlackScholes
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class GreeksCalculator(
    @Value("\${risk-free-rate:0.05}") private val riskFreeRate: Double
) {

    fun calculate(
        spot: BigDecimal,
        strike: BigDecimal,
        expiry: LocalDate,
        optionType: OptionType,
        iv: Double?,
        ibkrGreeks: Greeks?
    ): Greeks {
        if (ibkrGreeks != null) return ibkrGreeks

        val tte = ChronoUnit.DAYS.between(LocalDate.now(), expiry) / 365.0
        if (tte <= 0.0) return zeroGreeks()

        val volatility = iv ?: 0.20

        return Greeks(
            delta = BlackScholes.delta(spot, strike, tte, riskFreeRate, volatility, optionType),
            gamma = BlackScholes.gamma(spot, strike, tte, riskFreeRate, volatility),
            theta = BlackScholes.theta(spot, strike, tte, riskFreeRate, volatility, optionType),
            vega = BlackScholes.vega(spot, strike, tte, riskFreeRate, volatility),
            rho = BlackScholes.rho(spot, strike, tte, riskFreeRate, volatility, optionType),
            source = GreeksSource.BLACK_SCHOLES
        )
    }

    private fun zeroGreeks(): Greeks = Greeks(
        delta = BigDecimal.ZERO, gamma = BigDecimal.ZERO, theta = BigDecimal.ZERO,
        vega = BigDecimal.ZERO, rho = BigDecimal.ZERO, source = GreeksSource.BLACK_SCHOLES
    )
}
