package com.portfolio.broker.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class RebalanceService(
    private val groupAccountRepository: PortfolioGroupAccountRepository,
    private val settingsRepository: PortfolioGroupSettingsRepository,
    private val positionRepository: BrokerPositionRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val exchangeRateService: ExchangeRateService,
    private val driftCalculationService: DriftCalculationService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        val MIN_TRADE_AMOUNT = BigDecimal(10)
    }

    fun calculateRebalanceTrades(groupId: Long): RebalanceTradesResponse {
        val drift = driftCalculationService.calculateDrift(groupId)
        val settings = settingsRepository.findByGroupId(groupId)
        val sellToRebalance = settings?.sellToRebalance ?: false
        val keepCurrenciesSeparate = settings?.keepCurrenciesSeparate ?: false

        val trades = mutableListOf<RebalanceTradeDto>()
        val availableCash = drift.cash.toMutableMap()

        // Get positions by connection for trade placement
        val linkedAccounts = groupAccountRepository.findByGroupId(groupId)
        val positionsByConnection = linkedAccounts.associate { link ->
            link.connection.id to positionRepository.findCurrentPositionsByConnectionId(link.connection.id)
        }

        // Build a map of connectionId -> accountName for trade attribution
        val accountNames = linkedAccounts.associate { link ->
            link.connection.id to link.connection.accountName
        }

        // Step 1: Generate SELL trades for overweight positions (if enabled)
        if (sellToRebalance) {
            val overweight = drift.holdings
                .filter { it.driftPercent > BigDecimal.ZERO }
                .sortedByDescending { it.driftPercent }

            for (holding in overweight) {
                val excessValue = holding.actualValue - holding.targetValue
                if (excessValue < MIN_TRADE_AMOUNT) continue

                // Find which connection holds this position
                for ((connectionId, positions) in positionsByConnection) {
                    val position = positions.find { it.symbol == holding.symbol } ?: continue
                    val price = position.currentPrice ?: continue
                    if (price <= BigDecimal.ZERO) continue

                    val positionValue = position.currentValue ?: BigDecimal.ZERO
                    val sellAmount = excessValue.min(positionValue)
                    val units = sellAmount.divide(price, 4, RoundingMode.DOWN)
                    val actualSellAmount = units * price

                    if (actualSellAmount < MIN_TRADE_AMOUNT) continue

                    trades.add(RebalanceTradeDto(
                        action = "SELL",
                        symbol = holding.symbol,
                        securityName = holding.securityName,
                        units = units,
                        price = price,
                        amount = actualSellAmount,
                        currency = holding.currency,
                        accountName = accountNames[connectionId],
                        connectionId = connectionId
                    ))

                    // Add sell proceeds to available cash
                    val currency = holding.currency
                    availableCash[currency] = (availableCash[currency] ?: BigDecimal.ZERO) + actualSellAmount
                    break // Only sell from first connection that holds it
                }
            }
        }

        // Step 2: Generate BUY trades for underweight positions (most underweight first)
        val underweight = drift.holdings
            .filter { it.driftPercent < BigDecimal.ZERO }
            .sortedBy { it.driftPercent } // Most underweight first

        for (holding in underweight) {
            val deficit = holding.targetValue - holding.actualValue
            if (deficit < MIN_TRADE_AMOUNT) continue

            // Find a price for this symbol from positions
            val price = positionsByConnection.values
                .flatten()
                .find { it.symbol == holding.symbol }
                ?.currentPrice

            if (price == null || price <= BigDecimal.ZERO) continue

            val currency = holding.currency

            // Determine available cash for this buy
            val cashForBuy = if (keepCurrenciesSeparate) {
                availableCash[currency] ?: BigDecimal.ZERO
            } else {
                availableCash.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
            }

            if (cashForBuy < MIN_TRADE_AMOUNT) continue

            val buyAmount = deficit.min(cashForBuy)
            val units = buyAmount.divide(price, 4, RoundingMode.DOWN)
            val actualBuyAmount = units * price

            if (actualBuyAmount < MIN_TRADE_AMOUNT) continue

            // Pick a connection to place the buy
            val targetConnectionId = linkedAccounts.firstOrNull()?.connection?.id ?: continue

            trades.add(RebalanceTradeDto(
                action = "BUY",
                symbol = holding.symbol,
                securityName = holding.securityName,
                units = units,
                price = price,
                amount = actualBuyAmount,
                currency = currency,
                accountName = accountNames[targetConnectionId],
                connectionId = targetConnectionId
            ))

            // Deduct from available cash
            if (keepCurrenciesSeparate) {
                availableCash[currency] = (availableCash[currency] ?: BigDecimal.ZERO) - actualBuyAmount
            } else {
                // Deduct from matching currency first, then others
                var remaining = actualBuyAmount
                if (availableCash.containsKey(currency)) {
                    val deduct = remaining.min(availableCash[currency] ?: BigDecimal.ZERO)
                    availableCash[currency] = (availableCash[currency] ?: BigDecimal.ZERO) - deduct
                    remaining -= deduct
                }
                if (remaining > BigDecimal.ZERO) {
                    for (key in availableCash.keys.toList()) {
                        if (remaining <= BigDecimal.ZERO) break
                        val deduct = remaining.min(availableCash[key] ?: BigDecimal.ZERO)
                        availableCash[key] = (availableCash[key] ?: BigDecimal.ZERO) - deduct
                        remaining -= deduct
                    }
                }
            }
        }

        // Calculate resulting accuracy after trades
        val resultingAccuracy = calculateResultingAccuracy(drift, trades)

        log.info("Calculated {} rebalance trades for portfolio group {}", trades.size, groupId)

        return RebalanceTradesResponse(
            groupId = groupId,
            trades = trades,
            cashRemaining = availableCash,
            resultingAccuracy = resultingAccuracy
        )
    }

    private val cashTypeRef = object : TypeReference<Map<String, BigDecimal>>() {}

    fun calculateTradesForAccount(connection: BrokerConnection): PendingOrdersResponse {
        val model = connection.modelPortfolio
            ?: throw IllegalArgumentException("No model applied to connection ${connection.id}")

        val allocations = model.allocations
        val positions = positionRepository.findCurrentPositionsByConnectionId(connection.id)
        val today = LocalDate.now()
        val accountName = connection.accountName ?: ""

        // --- 1. Gather cash from balance snapshot ---
        val cashByCurrency = parseCashFromSnapshot(connection.id)
        var availableCashCAD = cashByCurrency.entries.fold(BigDecimal.ZERO) { acc, (currency, amount) ->
            val rate = getFxRateToCAD(currency, today)
            acc + amount * rate
        }
        log.debug("Account {} initial cash CAD: {}", connection.id, availableCashCAD)

        // --- 2. Calculate total portfolio value in CAD ---
        val totalPortfolioValueCAD = positions.fold(BigDecimal.ZERO) { acc, pos ->
            val value = pos.currentValue ?: BigDecimal.ZERO
            val rate = getFxRateToCAD(pos.currency, today)
            acc + value * rate
        } + availableCashCAD
        log.debug("Account {} total portfolio value CAD: {}", connection.id, totalPortfolioValueCAD)

        if (totalPortfolioValueCAD <= BigDecimal.ZERO) {
            return PendingOrdersResponse(
                connectionId = connection.id,
                orders = emptyList(),
                totalAmount = BigDecimal.ZERO,
                cashRemaining = availableCashCAD.setScale(2, RoundingMode.HALF_UP),
                cashWarning = "Portfolio has no value — cannot calculate rebalance trades"
            )
        }

        // --- 3. Classify positions ---
        val modelSymbols = allocations.map { it.symbol.uppercase() }.toSet()
        val positionsBySymbol = positions.associateBy { it.symbol.uppercase() }
        val nonModelPositions = positions.filter { it.symbol.uppercase() !in modelSymbols }

        val orders = mutableListOf<PendingOrderDto>()
        var cashWarning: String? = null

        // --- 4. Generate SELL orders for non-model positions (liquidate entirely) ---
        for (pos in nonModelPositions) {
            val price = pos.currentPrice ?: continue
            if (price <= BigDecimal.ZERO) continue
            val units = pos.quantity.setScale(0, RoundingMode.DOWN).toInt()
            if (units <= 0) continue

            val amount = price.multiply(BigDecimal(units))
            val rate = getFxRateToCAD(pos.currency, today)
            val proceedsCAD = amount * rate

            orders.add(PendingOrderDto(
                action = "SELL",
                symbol = pos.symbol,
                securityName = pos.securityName,
                units = units,
                price = price,
                amount = amount.setScale(2, RoundingMode.HALF_UP),
                currency = pos.currency,
                accountName = accountName,
                targetPercent = BigDecimal.ZERO,
                targetValue = BigDecimal.ZERO
            ))

            availableCashCAD += proceedsCAD
            log.debug("LIQUIDATE {} — {} units @ {} {}, proceeds CAD: {}",
                pos.symbol, units, price, pos.currency, proceedsCAD)
        }

        // Recalculate total portfolio value after liquidation proceeds are factored in
        // (proceeds replace position value, so total stays the same — but we track cash separately)

        // --- 5. Generate adjustment orders for existing model positions ---
        for (alloc in allocations) {
            val pos = positionsBySymbol[alloc.symbol.uppercase()] ?: continue
            val price = pos.currentPrice ?: continue
            if (price <= BigDecimal.ZERO) continue

            val rate = getFxRateToCAD(pos.currency, today)
            val currentValueCAD = (pos.currentValue ?: BigDecimal.ZERO) * rate
            val targetValueCAD = totalPortfolioValueCAD.multiply(alloc.targetPercent)
                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            val diffCAD = targetValueCAD - currentValueCAD

            if (diffCAD.abs() < MIN_TRADE_AMOUNT) continue

            if (diffCAD > BigDecimal.ZERO) {
                // BUY more units
                val priceCAD = price * rate
                val units = diffCAD.divide(priceCAD, 0, RoundingMode.DOWN).toInt()
                if (units <= 0) continue
                val amount = price.multiply(BigDecimal(units))
                val costCAD = priceCAD.multiply(BigDecimal(units))

                val insufficient = costCAD > availableCashCAD
                if (insufficient && cashWarning == null) {
                    cashWarning = "Insufficient cash to complete all BUY orders"
                }

                orders.add(PendingOrderDto(
                    action = "BUY",
                    symbol = alloc.symbol,
                    securityName = pos.securityName,
                    units = units,
                    price = price,
                    amount = amount.setScale(2, RoundingMode.HALF_UP),
                    currency = pos.currency,
                    accountName = accountName,
                    targetPercent = alloc.targetPercent,
                    targetValue = targetValueCAD.setScale(2, RoundingMode.HALF_UP),
                    cashInsufficient = insufficient
                ))

                availableCashCAD -= costCAD
            } else {
                // SELL excess units
                val priceCAD = price * rate
                val units = diffCAD.abs().divide(priceCAD, 0, RoundingMode.DOWN).toInt()
                if (units <= 0) continue
                val amount = price.multiply(BigDecimal(units))
                val proceedsCAD = priceCAD.multiply(BigDecimal(units))

                orders.add(PendingOrderDto(
                    action = "SELL",
                    symbol = alloc.symbol,
                    securityName = pos.securityName,
                    units = units,
                    price = price,
                    amount = amount.setScale(2, RoundingMode.HALF_UP),
                    currency = pos.currency,
                    accountName = accountName,
                    targetPercent = alloc.targetPercent,
                    targetValue = targetValueCAD.setScale(2, RoundingMode.HALF_UP)
                ))

                availableCashCAD += proceedsCAD
            }
        }

        // --- 6. Generate BUY orders for new positions (not currently held) ---
        for (alloc in allocations) {
            if (positionsBySymbol.containsKey(alloc.symbol.uppercase())) continue

            val targetValueCAD = totalPortfolioValueCAD.multiply(alloc.targetPercent)
                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

            if (targetValueCAD < MIN_TRADE_AMOUNT) continue

            // Look up price from other connections, then fall back to system-wide positions
            val priceAndCurrency = findPrice(connection, alloc.symbol)
            if (priceAndCurrency == null) {
                log.warn("No price available for symbol {} — skipping new position order", alloc.symbol)
                if (cashWarning == null) {
                    cashWarning = "Price unavailable for ${alloc.symbol} — some orders could not be generated"
                }
                continue
            }
            val (price, currency) = priceAndCurrency
            val rate = getFxRateToCAD(currency, today)
            val priceCAD = price * rate
            val units = targetValueCAD.divide(priceCAD, 0, RoundingMode.DOWN).toInt()
            if (units <= 0) continue

            val amount = price.multiply(BigDecimal(units))
            val costCAD = priceCAD.multiply(BigDecimal(units))

            val insufficient = costCAD > availableCashCAD
            if (insufficient && cashWarning == null) {
                cashWarning = "Insufficient cash to complete all BUY orders"
            }

            orders.add(PendingOrderDto(
                action = "BUY",
                symbol = alloc.symbol,
                securityName = null,
                units = units,
                price = price,
                amount = amount.setScale(2, RoundingMode.HALF_UP),
                currency = currency,
                accountName = accountName,
                targetPercent = alloc.targetPercent,
                targetValue = targetValueCAD.setScale(2, RoundingMode.HALF_UP),
                cashInsufficient = insufficient
            ))

            availableCashCAD -= costCAD
        }

        // --- 7. Sort: SELLs first (by amount DESC), then BUYs (by amount DESC) ---
        val sellOrders = orders.filter { it.action == "SELL" }.sortedByDescending { it.amount }
        val buyOrders = orders.filter { it.action == "BUY" }.sortedByDescending { it.amount }
        val sortedOrders = sellOrders + buyOrders

        val totalSellAmount = sellOrders.sumOf { it.amount }
        val totalBuyAmount = buyOrders.sumOf { it.amount }

        log.info("Account {} — {} sells ({}), {} buys ({}), cash remaining CAD: {}",
            connection.id, sellOrders.size, totalSellAmount, buyOrders.size, totalBuyAmount,
            availableCashCAD.setScale(2, RoundingMode.HALF_UP))

        return PendingOrdersResponse(
            connectionId = connection.id,
            orders = sortedOrders,
            totalAmount = totalSellAmount + totalBuyAmount,
            cashRemaining = availableCashCAD.setScale(2, RoundingMode.HALF_UP),
            cashWarning = cashWarning,
            totalSellAmount = totalSellAmount.setScale(2, RoundingMode.HALF_UP),
            totalBuyAmount = totalBuyAmount.setScale(2, RoundingMode.HALF_UP)
        )
    }

    /**
     * Parse cash amounts from the balance snapshot JSONB.
     * Returns a map of currency code -> cash amount.
     * The JSONB format is: {"cash_CAD": 29320.05, "buying_power_CAD": 3705.34}
     */
    private fun parseCashFromSnapshot(connectionId: Long): Map<String, BigDecimal> {
        val snapshot = balanceRepository.findLatestByConnectionId(connectionId)
            ?: return emptyMap()
        val cashJson = snapshot.cash ?: return emptyMap()

        return try {
            val parsed = objectMapper.readValue(cashJson, cashTypeRef)
            val result = mutableMapOf<String, BigDecimal>()
            for ((key, value) in parsed) {
                if (key.startsWith("cash_")) {
                    val currency = key.removePrefix("cash_").uppercase()
                    result[currency] = (result[currency] ?: BigDecimal.ZERO) + value
                } else if (key.length == 3 && key == key.uppercase() && !key.startsWith("buying")) {
                    // Simple currency code key (e.g., "CAD": 5000)
                    result[key] = (result[key] ?: BigDecimal.ZERO) + value
                }
                // Skip buying_power_ entries — we only want spendable cash
            }
            result
        } catch (e: Exception) {
            log.warn("Failed to parse cash JSONB for connection {}: {}", connectionId, e.message)
            emptyMap()
        }
    }

    /**
     * Get FX rate to convert from [currency] to CAD.
     * Returns BigDecimal.ONE for CAD (no conversion needed).
     */
    private fun getFxRateToCAD(currency: String, date: LocalDate): BigDecimal {
        if (currency.uppercase() == "CAD") return BigDecimal.ONE
        return exchangeRateService.getRate(currency, date) ?: run {
            log.warn("FX rate unavailable for {} — defaulting to 1.0", currency)
            BigDecimal.ONE
        }
    }

    /**
     * Search for the current price of a symbol across the user's other active connections.
     * Returns (price, currency) if found, null otherwise.
     */
    private fun findPriceFromOtherConnections(
        connection: BrokerConnection,
        symbol: String
    ): Pair<BigDecimal, String>? {
        val userConnections = connectionRepository.findByUserIdAndStatus(
            connection.user.id, ConnectionStatus.ACTIVE
        )
        val otherConnectionIds = userConnections.map { it.id }.filter { it != connection.id }

        for (connId in otherConnectionIds) {
            val positions = positionRepository.findCurrentPositionsByConnectionId(connId)
            val match = positions.find { it.symbol.equals(symbol, ignoreCase = true) }
            if (match?.currentPrice != null && match.currentPrice > BigDecimal.ZERO) {
                return match.currentPrice to match.currency
            }
        }
        return null
    }

    /**
     * Enhanced price lookup chain:
     * 1. Search the user's other active connections for a matching position price
     * 2. Fall back to ANY current position in the system for the symbol (market prices are public data)
     */
    private fun findPrice(connection: BrokerConnection, symbol: String): Pair<BigDecimal, String>? {
        // 1. Check other user connections (existing logic)
        val fromOtherConns = findPriceFromOtherConnections(connection, symbol)
        if (fromOtherConns != null) return fromOtherConns

        // 2. Check ALL positions in the system for this symbol (market prices are public)
        val anyPosition = positionRepository.findFirstBySymbolIgnoreCaseAndIsCurrentTrue(symbol)
        if (anyPosition?.currentPrice != null && anyPosition.currentPrice > BigDecimal.ZERO) {
            log.debug("Found price for {} from system-wide position lookup: {} {}",
                symbol, anyPosition.currentPrice, anyPosition.currency)
            return anyPosition.currentPrice to anyPosition.currency
        }

        return null
    }

    private fun calculateResultingAccuracy(
        drift: DriftAnalysisResponse,
        trades: List<RebalanceTradeDto>
    ): BigDecimal {
        if (drift.holdings.isEmpty()) return BigDecimal.ZERO

        // Project new values after trades
        val adjustments = mutableMapOf<String, BigDecimal>()
        for (trade in trades) {
            val sign = if (trade.action == "BUY") BigDecimal.ONE else BigDecimal.ONE.negate()
            adjustments[trade.symbol] = (adjustments[trade.symbol] ?: BigDecimal.ZERO) + (trade.amount * sign)
        }

        val projectedHoldings = drift.holdings.map { holding ->
            val adjustment = adjustments[holding.symbol] ?: BigDecimal.ZERO
            val newActualValue = holding.actualValue + adjustment
            holding.copy(actualValue = newActualValue)
        }

        val newTotalValue = projectedHoldings.sumOf { it.actualValue } +
            drift.cash.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }

        if (newTotalValue <= BigDecimal.ZERO) return BigDecimal.ZERO

        val drifts = projectedHoldings.map { holding ->
            val newActualPercent = holding.actualValue.multiply(BigDecimal(100))
                .divide(newTotalValue, 4, RoundingMode.HALF_UP)
            (newActualPercent - holding.targetPercent).abs()
        }

        val meanDrift = drifts.fold(BigDecimal.ZERO) { acc, d -> acc + d }
            .divide(BigDecimal(drifts.size), 4, RoundingMode.HALF_UP)

        return (BigDecimal(100) - meanDrift).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
    }
}
