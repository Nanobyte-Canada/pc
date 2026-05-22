package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.*
import com.portfolio.marketdata.api.dto.OptionsChainResponse
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

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

    private val log = LoggerFactory.getLogger(javaClass)

    private fun buildChainFromIbkr(underlying: String): OptionsChain? {
        // Get spot price from cache (populated by QuoteStreamingService) or snapshot
        val spotPrice = quoteCacheService.getQuote(underlying)?.last
            ?: ibkrClient.requestMarketDataSnapshot(
                ibkrClient.requestContractDetails(underlying, "STK").firstOrNull()?.conId ?: return null
            )?.last?.let { BigDecimal.valueOf(it) }
            ?: return null

        val contracts = try { ibkrClient.requestOptionChain(underlying) } catch (e: Exception) {
            log.error("Failed to load option chain for {}", underlying, e)
            return null
        }
        if (contracts.isEmpty()) return null

        val optionQuotes = contracts.mapNotNull { contract ->
            if (contract.expiry == null || contract.strike == null || contract.right == null) return@mapNotNull null
            val optionType = if (contract.right == "C") OptionType.CALL else OptionType.PUT

            val snapshot = if (contract.conId > 0) {
                ibkrClient.requestMarketDataSnapshot(contract.conId)
            } else null

            val bid = snapshot?.bid?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) }
                ?: BigDecimal.ZERO
            val ask = snapshot?.ask?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) }
                ?: BigDecimal.ZERO
            val last = snapshot?.last?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) }
                ?: bid.add(ask).divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP)

            val greeks = if (snapshot?.delta != null) {
                Greeks(
                    delta = BigDecimal.valueOf(snapshot.delta),
                    gamma = BigDecimal.valueOf(snapshot.gamma ?: 0.0),
                    theta = BigDecimal.valueOf(snapshot.theta ?: 0.0),
                    vega = BigDecimal.valueOf(snapshot.vega ?: 0.0),
                    rho = BigDecimal.ZERO,
                    source = GreeksSource.IBKR
                )
            } else null

            OptionQuote(
                underlying = underlying, optionType = optionType, strike = contract.strike,
                expiry = contract.expiry, bid = bid, ask = ask, last = last,
                volume = snapshot?.volume ?: 0L, openInterest = 0L,
                greeks = greeks, timestamp = Instant.now()
            )
        }

        return chainBuilder.build(underlying, spotPrice, optionQuotes)
    }

    private fun computeGreeks(chain: OptionsChain): OptionsChain {
        val updatedExpirations = chain.expirations.mapValues { (expiry, strikes) ->
            strikes.mapValues { (strike, strikeData) ->
                val updatedCall = strikeData.call?.let { call ->
                    if (call.greeks?.source == GreeksSource.IBKR) return@let call
                    call.copy(greeks = greeksCalculator.calculate(chain.spotPrice, strike, expiry, OptionType.CALL, 0.20, null))
                }
                val updatedPut = strikeData.put?.let { put ->
                    if (put.greeks?.source == GreeksSource.IBKR) return@let put
                    put.copy(greeks = greeksCalculator.calculate(chain.spotPrice, strike, expiry, OptionType.PUT, 0.20, null))
                }
                strikeData.copy(call = updatedCall, put = updatedPut)
            }
        }
        return chain.copy(expirations = updatedExpirations)
    }
}
