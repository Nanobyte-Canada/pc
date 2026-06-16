package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.*
import com.portfolio.marketdata.api.dto.OptionExpirationsResponse
import com.portfolio.marketdata.api.dto.OptionsChainResponse
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RestController
@RequestMapping("/api/v1/chains")
class ChainController(
    private val quoteCacheService: QuoteCacheService,
    private val chainBuilder: OptionsChainBuilder,
    private val greeksCalculator: GreeksCalculator,
    private val ibkrClient: IbkrClient
) {
    private val chainBuildExecutor: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "chain-build").apply { isDaemon = true }
    }

    @GetMapping("/{underlying}")
    fun getChain(@PathVariable underlying: String): ResponseEntity<OptionsChainResponse> {
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            return ResponseEntity.ok(OptionsChainResponse.fromDomain(cachedChain))
        }

        if (!ibkrClient.isConnected()) {
            log.warn("IBKR not connected, cannot fetch chain for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val chain = try {
            buildChainFromIbkr(underlying) ?: return ResponseEntity.notFound().build()
        } catch (e: ChainBuildTimeoutException) {
            log.warn("Chain build timed out for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
        quoteCacheService.cacheChain(underlying, chain)
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chain))
    }

    @GetMapping("/{underlying}/greeks")
    fun getChainWithGreeks(@PathVariable underlying: String): ResponseEntity<OptionsChainResponse> {
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            return ResponseEntity.ok(OptionsChainResponse.fromDomain(computeGreeks(cachedChain)))
        }

        if (!ibkrClient.isConnected()) {
            log.warn("IBKR not connected, cannot fetch chain for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val chain = try {
            buildChainFromIbkr(underlying) ?: return ResponseEntity.notFound().build()
        } catch (e: ChainBuildTimeoutException) {
            log.warn("Chain build timed out for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
        val chainWithGreeks = computeGreeks(chain)
        quoteCacheService.cacheChain(underlying, chainWithGreeks)
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chainWithGreeks))
    }

    @GetMapping("/{underlying}/expirations")
    fun getExpirations(@PathVariable underlying: String): ResponseEntity<OptionExpirationsResponse> {
        if (!ibkrClient.isConnected()) {
            log.warn("IBKR not connected, cannot fetch expirations for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val spotPrice = resolveSpotPrice(underlying) ?: return ResponseEntity.notFound().build()
        val expirations = ibkrClient.requestOptionExpirations(underlying)
        if (expirations.isEmpty()) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice, expirations))
    }

    @GetMapping("/{underlying}/expiry/{expiry}")
    fun getChainForExpiry(
        @PathVariable underlying: String,
        @PathVariable expiry: String,
        @RequestParam(defaultValue = "0.45") maxDelta: Double,
        @RequestParam(defaultValue = "25") strikesPerSide: Int
    ): ResponseEntity<OptionsChainResponse> {
        if (!ibkrClient.isConnected()) {
            log.warn("IBKR not connected, cannot fetch chain for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        val expiryDate = try { LocalDate.parse(expiry) } catch (_: Exception) {
            return ResponseEntity.badRequest().build()
        }
        val chain = try {
            buildChainForExpiry(underlying, expiryDate, maxDelta, strikesPerSide) ?: return ResponseEntity.notFound().build()
        } catch (e: ChainBuildTimeoutException) {
            log.warn("Chain build timed out for {} expiry {}", underlying, expiryDate)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chain))
    }

    private val log = LoggerFactory.getLogger(javaClass)
    companion object {
        private const val DEFAULT_MAX_DELTA = 0.45
        private const val ESTIMATED_IV = 0.30
    }

    private class ChainBuildTimeoutException(underlying: String, expiry: LocalDate? = null) : RuntimeException(
        "Chain build timed out for $underlying${expiry?.let { " expiry $it" } ?: ""}"
    )

    private fun buildChainFromIbkr(underlying: String): OptionsChain? {
        val future = CompletableFuture.supplyAsync({
            val spotPrice = resolveSpotPrice(underlying) ?: return@supplyAsync null

            val contracts = try { ibkrClient.requestOptionChain(underlying) } catch (e: Exception) {
                log.error("Failed to load option chain for {}", underlying, e)
                return@supplyAsync null
            }
            if (contracts.isEmpty()) return@supplyAsync null

            val firstExpiry = contracts.filter { it.expiry != null }.minByOrNull { it.expiry!! }?.expiry
            val snapshotContracts = filterByDelta(contracts, spotPrice, firstExpiry, DEFAULT_MAX_DELTA)
                .sortedBy { Math.abs(it.strike!!.toDouble() - spotPrice.toDouble()) }
                .take(30)

            log.info("Fetching snapshots for {} contracts (expiry {}) out of {} total for {}",
                snapshotContracts.size, firstExpiry, contracts.size, underlying)

            val snapshots = fetchSnapshots(snapshotContracts)

            val allDeltaFiltered = filterByDelta(contracts, spotPrice, null, DEFAULT_MAX_DELTA)
            val optionQuotes = allDeltaFiltered.mapNotNull { contract ->
                buildOptionQuote(underlying, contract, spotPrice, snapshots)
            }

            chainBuilder.build(underlying, spotPrice, optionQuotes)
        }, chainBuildExecutor)

        return try {
            future.get(15, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw ChainBuildTimeoutException(underlying)
        } catch (e: CancellationException) {
            log.warn("Chain build cancelled for {}", underlying)
            null
        } catch (e: Exception) {
            log.error("Failed to build option chain for {}", underlying, e)
            null
        }
    }

    private fun buildChainForExpiry(underlying: String, expiry: LocalDate, maxDelta: Double, strikesPerSide: Int = 25): OptionsChain? {
        val future = CompletableFuture.supplyAsync({
            val spotPrice = resolveSpotPrice(underlying) ?: return@supplyAsync null

            val contracts = try {
                ibkrClient.requestContractDetails(underlying, "OPT", expiry).filter { c ->
                    c.tradingClass == null || c.tradingClass == underlying
                }.ifEmpty { ibkrClient.requestContractDetails(underlying, "OPT", expiry) }
            } catch (e: Exception) {
                log.error("Failed to load contracts for {} expiry {}", underlying, expiry, e)
                return@supplyAsync null
            }
            if (contracts.isEmpty()) return@supplyAsync null

            val filtered = filterByStrikeCount(contracts, spotPrice, expiry, strikesPerSide)

            log.info("Building chain: {} contracts ({} per side) for {} expiry {}", filtered.size, strikesPerSide, underlying, expiry)

            val snapshots = emptyMap<Int, com.portfolio.marketdata.ibkr.MarketDataSnapshot>()

            val optionQuotes = filtered.mapNotNull { contract ->
                buildOptionQuote(underlying, contract, spotPrice, snapshots)
            }

            chainBuilder.build(underlying, spotPrice, optionQuotes)
        }, chainBuildExecutor)

        return try {
            future.get(15, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw ChainBuildTimeoutException(underlying, expiry)
        } catch (e: CancellationException) {
            log.warn("Chain build cancelled for {} expiry {}", underlying, expiry)
            null
        } catch (e: Exception) {
            log.error("Failed to build option chain for {} expiry {}", underlying, expiry, e)
            null
        }
    }

    private fun filterByStrikeCount(
        contracts: List<com.portfolio.marketdata.ibkr.OptionContractDetails>,
        spotPrice: BigDecimal, targetExpiry: LocalDate, strikesPerSide: Int
    ): List<com.portfolio.marketdata.ibkr.OptionContractDetails> {
        val eligible = contracts.filter { c ->
            c.conId > 0 && c.strike != null && c.expiry == targetExpiry && c.right != null
        }
        val spotDouble = spotPrice.toDouble()
        val uniqueStrikes = eligible.map { it.strike!!.toDouble() }.distinct().sorted()
        val below = uniqueStrikes.filter { it <= spotDouble }.takeLast(strikesPerSide).toSet()
        val above = uniqueStrikes.filter { it > spotDouble }.take(strikesPerSide).toSet()
        val validStrikes = below + above
        return eligible.filter { it.strike!!.toDouble() in validStrikes }
    }

    private fun resolveSpotPrice(underlying: String): BigDecimal? {
        return quoteCacheService.getQuote(underlying)?.last
            ?: ibkrClient.requestMarketDataSnapshot(
                ibkrClient.requestContractDetails(underlying, "STK").firstOrNull()?.conId ?: return null
            )?.last?.let { BigDecimal.valueOf(it) }
    }

    private fun filterByDelta(
        contracts: List<com.portfolio.marketdata.ibkr.OptionContractDetails>,
        spotPrice: BigDecimal, targetExpiry: LocalDate?, maxDelta: Double
    ): List<com.portfolio.marketdata.ibkr.OptionContractDetails> {
        val eligible = contracts.filter { c ->
            c.conId > 0 && c.strike != null && c.expiry != null && c.right != null &&
            (targetExpiry == null || c.expiry == targetExpiry)
        }
        val spotDouble = spotPrice.toDouble()
        val validStrikes = eligible.groupBy { "${it.expiry}:${it.strike}" }
            .filter { (_, group) ->
                val strike = group.first().strike!!.toDouble()
                val nearATM = Math.abs(strike - spotDouble) / spotDouble <= 0.05
                nearATM || group.any { c ->
                    val optionType = if (isCall(c.right)) OptionType.CALL else OptionType.PUT
                    val greeks = greeksCalculator.calculate(spotPrice, c.strike!!, c.expiry!!, optionType, ESTIMATED_IV, null)
                    greeks.delta.abs().toDouble() in 0.03..maxDelta
                }
            }.keys
        return eligible.filter { "${it.expiry}:${it.strike}" in validStrikes }
    }

    private fun fetchSnapshots(contracts: List<com.portfolio.marketdata.ibkr.OptionContractDetails>): Map<Int, com.portfolio.marketdata.ibkr.MarketDataSnapshot> {
        return contracts.mapNotNull { contract ->
            val snapshot = ibkrClient.requestMarketDataSnapshot(contract.conId)
            if (snapshot != null) contract.conId to snapshot else null
        }.toMap()
    }

    private fun isCall(right: String?) = right == "C" || right.equals("Call", ignoreCase = true)

    private fun buildOptionQuote(
        underlying: String,
        contract: com.portfolio.marketdata.ibkr.OptionContractDetails,
        spotPrice: BigDecimal,
        snapshots: Map<Int, com.portfolio.marketdata.ibkr.MarketDataSnapshot>
    ): OptionQuote? {
        if (contract.expiry == null || contract.strike == null || contract.right == null) return null
        val optionType = if (isCall(contract.right)) OptionType.CALL else OptionType.PUT
        val snapshot = snapshots[contract.conId]

        val bid = snapshot?.bid?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) } ?: BigDecimal.ZERO
        val ask = snapshot?.ask?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) } ?: BigDecimal.ZERO
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
        } else {
            greeksCalculator.calculate(spotPrice, contract.strike, contract.expiry, optionType, ESTIMATED_IV, null)
        }

        return OptionQuote(
            underlying = underlying, optionType = optionType, strike = contract.strike,
            expiry = contract.expiry, bid = bid, ask = ask, last = last,
            volume = snapshot?.volume ?: 0L, openInterest = 0L,
            greeks = greeks, timestamp = Instant.now()
        )
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
