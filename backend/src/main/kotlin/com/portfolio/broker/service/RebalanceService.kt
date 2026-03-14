package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class RebalanceService(
    private val groupAccountRepository: PortfolioGroupAccountRepository,
    private val settingsRepository: PortfolioGroupSettingsRepository,
    private val positionRepository: BrokerPositionRepository,
    private val driftCalculationService: DriftCalculationService
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
