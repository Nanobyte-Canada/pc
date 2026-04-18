package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.OptionQuote
import com.portfolio.common.domain.OptionType
import com.portfolio.common.domain.OptionsChain
import com.portfolio.marketdata.api.dto.OptionsChainResponse
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.FakeIbkrClient
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.random.Random

@RestController
@RequestMapping("/api/v1/chains")
class ChainController(
    private val quoteCacheService: QuoteCacheService,
    private val chainBuilder: OptionsChainBuilder,
    private val greeksCalculator: GreeksCalculator,
    private val ibkrClient: IbkrClient
) {

    @GetMapping("/{underlying}")
    fun getChain(@PathVariable underlying: String): ResponseEntity<OptionsChainResponse> {
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            return ResponseEntity.ok(OptionsChainResponse.fromDomain(cachedChain))
        }

        val chain = buildChainFromIbkr(underlying) ?: return ResponseEntity.notFound().build()
        quoteCacheService.cacheChain(underlying, chain)
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chain))
    }

    @GetMapping("/{underlying}/greeks")
    fun getChainWithGreeks(@PathVariable underlying: String): ResponseEntity<OptionsChainResponse> {
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            return ResponseEntity.ok(OptionsChainResponse.fromDomain(computeGreeks(cachedChain)))
        }

        val chain = buildChainFromIbkr(underlying) ?: return ResponseEntity.notFound().build()
        val chainWithGreeks = computeGreeks(chain)
        quoteCacheService.cacheChain(underlying, chainWithGreeks)
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chainWithGreeks))
    }

    private fun buildChainFromIbkr(underlying: String): OptionsChain? {
        val spot = (ibkrClient as? FakeIbkrClient)?.getSpotPrice(underlying) ?: return null
        val spotPrice = BigDecimal.valueOf(spot)

        val contracts = try { ibkrClient.requestOptionChain(underlying) } catch (e: Exception) { return null }
        if (contracts.isEmpty()) return null

        val optionQuotes = contracts.mapNotNull { contract ->
            if (contract.expiry == null || contract.strike == null || contract.right == null) return@mapNotNull null
            val optionType = if (contract.right == "C") OptionType.CALL else OptionType.PUT

            val intrinsic = when (optionType) {
                OptionType.CALL -> maxOf(spotPrice - contract.strike, BigDecimal.ZERO)
                OptionType.PUT -> maxOf(contract.strike - spotPrice, BigDecimal.ZERO)
            }
            val timeValue = BigDecimal.valueOf(2.0 + Random.nextDouble(0.0, 3.0))
            val mid = (intrinsic + timeValue).setScale(4, RoundingMode.HALF_UP)
            val spread = BigDecimal.valueOf(0.05 + Random.nextDouble(0.0, 0.15))
            val bid = (mid - spread.divide(BigDecimal.valueOf(2))).setScale(4, RoundingMode.HALF_UP)
            val ask = (mid + spread.divide(BigDecimal.valueOf(2))).setScale(4, RoundingMode.HALF_UP)

            OptionQuote(
                underlying = underlying, optionType = optionType, strike = contract.strike,
                expiry = contract.expiry, bid = bid, ask = ask, last = mid,
                volume = Random.nextLong(100, 10000), openInterest = Random.nextLong(500, 50000),
                greeks = null, timestamp = Instant.now()
            )
        }

        return chainBuilder.build(underlying, spotPrice, optionQuotes)
    }

    private fun computeGreeks(chain: OptionsChain): OptionsChain {
        val updatedExpirations = chain.expirations.mapValues { (expiry, strikes) ->
            strikes.mapValues { (strike, strikeData) ->
                val updatedCall = strikeData.call?.let { call ->
                    call.copy(greeks = greeksCalculator.calculate(chain.spotPrice, strike, expiry, OptionType.CALL, 0.20, null))
                }
                val updatedPut = strikeData.put?.let { put ->
                    put.copy(greeks = greeksCalculator.calculate(chain.spotPrice, strike, expiry, OptionType.PUT, 0.20, null))
                }
                strikeData.copy(call = updatedCall, put = updatedPut)
            }
        }
        return chain.copy(expirations = updatedExpirations)
    }
}
