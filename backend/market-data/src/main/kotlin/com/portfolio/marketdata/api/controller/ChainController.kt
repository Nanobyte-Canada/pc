package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.*
import com.portfolio.marketdata.api.dto.OptionExpirationsResponse
import com.portfolio.marketdata.api.dto.OptionsChainResponse
import com.portfolio.marketdata.config.AppProperties
import com.portfolio.marketdata.distribution.ExpiryCacheService
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import com.portfolio.marketdata.processing.GreeksCalculator
import com.portfolio.marketdata.processing.OptionsChainBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
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
import java.time.temporal.ChronoUnit
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

@RestController
@RequestMapping("/api/v1/chains")
class ChainController(
    private val quoteCacheService: QuoteCacheService,
    private val chainBuilder: OptionsChainBuilder,
    private val greeksCalculator: GreeksCalculator,
    private val ibkrClient: IbkrClient,
    private val properties: AppProperties,
    @Value("\${chain.build-timeout-seconds:25}") private val buildTimeoutSeconds: Long,
    @Value("\${chain.build-max-threads:4}") private val buildMaxThreads: Int,
    private val expiryCacheService: ExpiryCacheService
) : DisposableBean {

    // Per-contract snapshot fetch timeout in seconds. Used by fetchSnapshots for parallel snapshot requests.
    @Value("\${chain.snapshot-timeout-seconds:8}") private val snapshotTimeoutSeconds: Long = 8

    private val chainBuildExecutor: ExecutorService = Executors.newFixedThreadPool(buildMaxThreads.coerceIn(1, 64)) { r ->
        Thread(r, "chain-build-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
    }

    private val snapshotExecutor: ExecutorService = Executors.newFixedThreadPool(12) { r ->
        Thread(r, "chain-snapshot-${snapshotThreadCounter.incrementAndGet()}").apply { isDaemon = true }
    }

    private val effectiveBuildTimeoutSeconds: Long = buildTimeoutSeconds.coerceAtLeast(1L)
    private val effectiveSnapshotTimeoutSeconds: Long = snapshotTimeoutSeconds.coerceAtLeast(1L)

    override fun destroy() {
        chainBuildExecutor.shutdownNow()
        snapshotExecutor.shutdownNow()
    }

    @GetMapping("/{underlying}")
    fun getChain(@PathVariable underlying: String): ResponseEntity<OptionsChainResponse> {
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            if (!ibkrClient.isConnected()) {
                log.warn("Serving stale chain from cache for {} because IBKR is disconnected", underlying)
            }
            return ResponseEntity.ok(OptionsChainResponse.fromDomain(cachedChain))
        }

        if (!checkConnected(underlying)) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()

        val chain = try {
            buildChainFromIbkr(underlying) ?: return ResponseEntity.notFound().build()
        } catch (e: ChainBuildTimeoutException) {
            log.warn("Chain build timed out for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        } catch (e: ChainBuildUnavailableException) {
            log.warn("IBKR unavailable during chain build for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
        quoteCacheService.cacheChain(underlying, chain)
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chain))
    }

    @GetMapping("/{underlying}/greeks")
    fun getChainWithGreeks(@PathVariable underlying: String): ResponseEntity<OptionsChainResponse> {
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            if (!ibkrClient.isConnected()) {
                log.warn("Serving stale chain from cache for {} because IBKR is disconnected", underlying)
            }
            return ResponseEntity.ok(OptionsChainResponse.fromDomain(computeGreeks(cachedChain)))
        }

        if (!checkConnected(underlying)) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()

        val chain = try {
            buildChainFromIbkr(underlying) ?: return ResponseEntity.notFound().build()
        } catch (e: ChainBuildTimeoutException) {
            log.warn("Chain build timed out for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        } catch (e: ChainBuildUnavailableException) {
            log.warn("IBKR unavailable during chain build for {}", underlying)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
        val chainWithGreeks = computeGreeks(chain)
        quoteCacheService.cacheChain(underlying, chainWithGreeks)
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chainWithGreeks))
    }

    @GetMapping("/{underlying}/expirations")
    fun getExpirations(
        @PathVariable underlying: String,
        @RequestParam(required = false) maxDte: Int?
    ): ResponseEntity<OptionExpirationsResponse> {
        val spotPrice = quoteCacheService.getQuote(underlying)?.last

        // Tier 1: Check ExpiryCacheService (90-day cache)
        val cachedExpiry = expiryCacheService.getExpiry(underlying)
        if (cachedExpiry != null) {
            val filtered = filterByDte(cachedExpiry, maxDte)
            return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice ?: BigDecimal.ZERO, filtered))
        }

        // Tier 2: Check full chain cache
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            val expirations = cachedChain.expirations.keys.toList().sorted()
            val filtered = filterByDte(expirations, maxDte)
            return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice ?: cachedChain.spotPrice, filtered))
        }

        // Tier 3: Check IBKR connection
        if (!ibkrClient.isConnected()) {
            log.warn("IBKR not connected, returning empty expirations for {}", underlying)
            return ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice ?: BigDecimal.ZERO, emptyList()))
        }

        // Tier 4: Fetch from IBKR (on-demand fallback)
        return try {
            val expirations = ibkrClient.requestOptionExpirations(underlying).sorted()
            expiryCacheService.cacheExpiry(underlying, expirations)
            log.info("Fetched and cached {} expirations for {} from IBKR", expirations.size, underlying)
            val filtered = filterByDte(expirations, maxDte)
            ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice ?: BigDecimal.ZERO, filtered))
        } catch (e: Exception) {
            log.error("Failed to fetch expirations for {} from IBKR: {}", underlying, e.message)
            ResponseEntity.ok(OptionExpirationsResponse(underlying, spotPrice ?: BigDecimal.ZERO, emptyList()))
        }
    }

    @GetMapping("/{underlying}/expiry/{expiry}")
    fun getChainForExpiry(
        @PathVariable underlying: String,
        @PathVariable expiry: String,
        @RequestParam(defaultValue = "0.45") maxDelta: Double,
        @RequestParam(defaultValue = "25") strikesPerSide: Int,
        @RequestParam(defaultValue = "both") side: String
    ): ResponseEntity<OptionsChainResponse> {
        val cachedChain = quoteCacheService.getChain(underlying)
        if (cachedChain != null) {
            if (!ibkrClient.isConnected()) {
                log.warn("Serving stale chain from cache for {} because IBKR is disconnected", underlying)
            }
            return ResponseEntity.ok(OptionsChainResponse.fromDomain(cachedChain))
        }

        if (!checkConnected(underlying)) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()

        val expiryDate = try { LocalDate.parse(expiry) } catch (_: Exception) {
            return ResponseEntity.badRequest().build()
        }
        val chain = try {
            buildChainForExpiry(underlying, expiryDate, maxDelta, strikesPerSide, side) ?: return ResponseEntity.notFound().build()
        } catch (e: ChainBuildTimeoutException) {
            log.warn("Chain build timed out for {} expiry {}", underlying, expiryDate)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        } catch (e: ChainBuildUnavailableException) {
            log.warn("IBKR unavailable during chain build for {} expiry {}", underlying, expiryDate)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
        // Cache the expiry-specific chain to avoid redundant IBKR fetches.
        // Merge into existing cached chain if available to preserve other expiry data.
        val existingCached = quoteCacheService.getChain(underlying)
        if (existingCached != null) {
            val merged = existingCached.copy(
                expirations = existingCached.expirations + chain.expirations
            )
            quoteCacheService.cacheChain(underlying, merged)
        } else {
            quoteCacheService.cacheChain(underlying, chain)
        }
        return ResponseEntity.ok(OptionsChainResponse.fromDomain(chain))
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /** Thrown when a chain build exceeds the configured timeout (chain.build-timeout-seconds). */
    private class ChainBuildTimeoutException(underlying: String, expiry: LocalDate? = null) : RuntimeException(
        "Chain build timed out for ${underlying.replace(Regex("[\\r\\n]"), "")}${expiry?.let { " expiry $it" } ?: ""}"
    )

    /** Thrown when IBKR disconnects during an async chain build to distinguish from actual timeouts. */
    private class ChainBuildUnavailableException(underlying: String, expiry: LocalDate? = null) : RuntimeException(
        "IBKR disconnected during chain build for ${underlying.replace(Regex("[\\r\\n]"), "")}${expiry?.let { " expiry $it" } ?: ""}"
    )

    companion object {
        private const val DEFAULT_MAX_DELTA = 0.45
        private const val ESTIMATED_IV = 0.30
        private val threadCounter = AtomicInteger(0)
        private val snapshotThreadCounter = AtomicInteger(0)
    }

    private fun checkConnected(underlying: String): Boolean {
        if (!ibkrClient.isConnected()) {
            log.warn("IBKR not connected, cannot fetch chain for {}", underlying)
            return false
        }
        return true
    }

    private fun filterByDte(expirations: List<LocalDate>, maxDte: Int?): List<LocalDate> {
        val effectiveMaxDte = maxDte ?: properties.maxDteDefault
        val today = LocalDate.now()
        return expirations.filter { ChronoUnit.DAYS.between(today, it) in 0..effectiveMaxDte.toLong() }
    }

    private fun buildChainFromIbkr(underlying: String): OptionsChain? {
        val future = CompletableFuture.supplyAsync({
            if (Thread.interrupted()) throw InterruptedException()
            if (!ibkrClient.isConnected()) {
                log.warn("IBKR disconnected before async chain build for {}", underlying)
                throw ChainBuildUnavailableException(underlying)
            }
            val spotPrice = resolveSpotPrice(underlying) ?: return@supplyAsync null
            if (Thread.interrupted()) throw InterruptedException()

            val contracts = try { ibkrClient.requestOptionChain(underlying) } catch (e: Exception) {
                log.error("Failed to load option chain for {}", underlying, e)
                return@supplyAsync null
            }
            if (contracts.isEmpty()) return@supplyAsync null

            val allDeltaFiltered = filterByDelta(contracts, spotPrice, null, DEFAULT_MAX_DELTA)

            log.info("Fetching snapshots for {} delta-filtered contracts out of {} total for {}",
                allDeltaFiltered.size, contracts.size, underlying)

            val snapshots = fetchSnapshots(allDeltaFiltered)

            val optionQuotes = allDeltaFiltered.mapNotNull { contract ->
                buildOptionQuote(underlying, contract, spotPrice, snapshots)
            }

            chainBuilder.build(underlying, spotPrice, optionQuotes)
        }, chainBuildExecutor)

        return try {
            future.get(effectiveBuildTimeoutSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            // cancel(false) is intentional: IBKR calls are not interrupt-safe and cancel(true) may leave
            // client state inconsistent. The thread will finish its current work and then exit normally.
            future.cancel(false)
            throw ChainBuildTimeoutException(underlying)
        } catch (e: CancellationException) {
            log.warn("Chain build cancelled for {}", underlying, e)
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("Chain build interrupted for {}", underlying)
            null
        } catch (e: ExecutionException) {
            val cause = e.cause
            when (cause) {
                is ChainBuildTimeoutException -> throw cause
                is ChainBuildUnavailableException -> throw cause
            }
            log.error("Failed to build option chain for {}", underlying, e)
            null
        } catch (e: Exception) {
            log.error("Failed to build option chain for {}", underlying, e)
            null
        }
    }

    private fun buildChainForExpiry(underlying: String, expiry: LocalDate, maxDelta: Double, strikesPerSide: Int = 25, side: String = "both"): OptionsChain? {
        val future = CompletableFuture.supplyAsync({
            if (Thread.interrupted()) throw InterruptedException()
            if (!ibkrClient.isConnected()) {
                log.warn("IBKR disconnected before async chain build for {} expiry {}", underlying, expiry)
                throw ChainBuildUnavailableException(underlying, expiry)
            }
            val spotPrice = resolveSpotPrice(underlying) ?: return@supplyAsync null
            if (Thread.interrupted()) throw InterruptedException()

            val contracts = try {
                ibkrClient.requestContractDetails(underlying, "OPT", expiry).filter { c ->
                    c.tradingClass == null || c.tradingClass == underlying
                }
            } catch (e: Exception) {
                log.error("Failed to load contracts for {} expiry {}", underlying, expiry, e)
                return@supplyAsync null
            }
            if (contracts.isEmpty()) return@supplyAsync null

            val sideFiltered = when (side.lowercase()) {
                "put" -> contracts.filter { it.right?.uppercase() in setOf("P", "PUT") }
                "call" -> contracts.filter { it.right?.uppercase() in setOf("C", "CALL") }
                else -> contracts
            }
            if (sideFiltered.isEmpty()) return@supplyAsync null

            val filtered = filterByStrikeCount(sideFiltered, spotPrice, expiry, strikesPerSide)

            log.info("Fetching snapshots for {} contracts ({} per side, side={}) for {} expiry {}",
                filtered.size, strikesPerSide, side, underlying, expiry)

            val snapshots = fetchSnapshots(filtered)

            val optionQuotes = filtered.mapNotNull { contract ->
                buildOptionQuote(underlying, contract, spotPrice, snapshots)
            }

            chainBuilder.build(underlying, spotPrice, optionQuotes)
        }, chainBuildExecutor)

        return try {
            future.get(effectiveBuildTimeoutSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            // cancel(false) is intentional: IBKR calls are not interrupt-safe and cancel(true) may leave
            // client state inconsistent. The thread will finish its current work and then exit normally.
            future.cancel(false)
            throw ChainBuildTimeoutException(underlying, expiry)
        } catch (e: CancellationException) {
            log.warn("Chain build cancelled for {} expiry {}", underlying, expiry, e)
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("Chain build interrupted for {} expiry {}", underlying, expiry)
            null
        } catch (e: ExecutionException) {
            val cause = e.cause
            when (cause) {
                is ChainBuildTimeoutException -> throw cause
                is ChainBuildUnavailableException -> throw cause
            }
            log.error("Failed to build option chain for {} expiry {}", underlying, expiry, e)
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
        if (contracts.isEmpty()) return emptyMap()

        val startTime = System.currentTimeMillis()

        val futures = contracts.map { contract ->
            CompletableFuture.supplyAsync({
                // Interrupt check is for executor shutdown (shutdownNow) safety, not timeout cancellation.
                // Timeout uses cancel(false) since IBKR calls are not interrupt-safe.
                if (Thread.interrupted()) return@supplyAsync null
                try {
                    ibkrClient.requestMarketDataSnapshot(contract.conId)?.let { contract.conId to it }
                } catch (e: Exception) {
                    log.debug("Snapshot failed for conId={}", contract.conId)
                    null
                }
            }, snapshotExecutor)
        }

        val results = futures.mapNotNull { f ->
            if (Thread.interrupted()) return emptyMap()
            try {
                f.get(effectiveSnapshotTimeoutSeconds, TimeUnit.SECONDS)
            } catch (_: Exception) {
                null
            }
        }.toMap()

        log.info("Fetched {}/{} snapshots in {}ms (parallel)",
            results.size, contracts.size, System.currentTimeMillis() - startTime)

        return results
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

        val bid = snapshot?.bid?.takeIf { it >= 0 }?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) } ?: BigDecimal.ZERO
        val ask = snapshot?.ask?.takeIf { it >= 0 }?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) } ?: BigDecimal.ZERO
        val last = snapshot?.last?.takeIf { it > 0 }?.let { BigDecimal.valueOf(it).setScale(4, RoundingMode.HALF_UP) }
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
