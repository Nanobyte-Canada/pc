package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.BrokerPosition
import com.portfolio.broker.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

@Service
class DriftCalculationService(
    private val groupRepository: PortfolioGroupRepository,
    private val groupAccountRepository: PortfolioGroupAccountRepository,
    private val targetRepository: PortfolioTargetRepository,
    private val excludedAssetRepository: PortfolioExcludedAssetRepository,
    private val positionRepository: BrokerPositionRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun calculateDrift(groupId: Long): DriftAnalysisResponse {
        val group = groupRepository.findById(groupId).orElseThrow {
            IllegalArgumentException("Portfolio group not found: $groupId")
        }

        val linkedAccounts = groupAccountRepository.findByGroupId(groupId)
        val targets = targetRepository.findByGroupId(groupId)
        val excludedSymbols = excludedAssetRepository.findByGroupId(groupId).map { it.symbol }.toSet()

        // Gather positions across all linked accounts
        val allPositions = mutableListOf<BrokerPosition>()
        val cashByConnection = mutableMapOf<Long, Map<String, BigDecimal>>()

        for (link in linkedAccounts) {
            val connectionId = link.connection.id
            val positions = positionRepository.findCurrentPositionsByConnectionId(connectionId)
            allPositions.addAll(positions)

            // Get cash from latest balance snapshot
            val latestBalance = balanceRepository.findLatestByConnectionId(connectionId)
            if (latestBalance != null && latestBalance.cash != null) {
                try {
                    val cashMap = parseCashJson(latestBalance.cash)
                    cashByConnection[connectionId] = cashMap
                } catch (e: Exception) {
                    log.warn("Failed to parse cash JSON for connection {}: {}", connectionId, e.message)
                }
            }
        }

        // Aggregate cash across all connections
        val totalCash = mutableMapOf<String, BigDecimal>()
        cashByConnection.values.forEach { cashMap ->
            cashMap.forEach { (currency, amount) ->
                totalCash[currency] = (totalCash[currency] ?: BigDecimal.ZERO) + amount
            }
        }
        val totalCashValue = totalCash.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }

        // Aggregate positions by symbol (excluding excluded assets)
        val positionsBySymbol = allPositions
            .filter { it.symbol !in excludedSymbols }
            .groupBy { it.symbol }

        val totalPositionValue = positionsBySymbol.values.sumOf { positions ->
            positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        }

        val totalValue = totalPositionValue + totalCashValue

        // Build drift holdings
        val targetSymbols = targets.map { it.symbol }.toSet()
        val holdings = mutableListOf<DriftHoldingDto>()

        for (target in targets) {
            val positions = positionsBySymbol[target.symbol] ?: emptyList()
            val actualValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO }
            val actualPercent = if (totalValue > BigDecimal.ZERO) {
                actualValue.multiply(BigDecimal(100)).divide(totalValue, 4, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val driftPercent = actualPercent - target.targetPercent
            val targetValue = if (totalValue > BigDecimal.ZERO) {
                totalValue.multiply(target.targetPercent).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val securityName = positions.firstOrNull()?.securityName
            val currency = positions.firstOrNull()?.currency ?: "CAD"

            holdings.add(DriftHoldingDto(
                symbol = target.symbol,
                securityName = securityName,
                targetPercent = target.targetPercent,
                actualPercent = actualPercent,
                driftPercent = driftPercent,
                actualValue = actualValue,
                targetValue = targetValue,
                currency = currency
            ))
        }

        // Excluded assets with their values
        val excludedAssetDtos = allPositions
            .filter { it.symbol in excludedSymbols }
            .groupBy { it.symbol }
            .map { (symbol, positions) ->
                ExcludedAssetDto(
                    symbol = symbol,
                    securityName = positions.firstOrNull()?.securityName,
                    currentValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO },
                    currency = positions.firstOrNull()?.currency
                )
            }

        // Detect new assets (in positions but not in targets or exclusions)
        val newAssets = positionsBySymbol
            .filter { it.key !in targetSymbols }
            .map { (symbol, positions) ->
                NewAssetDto(
                    symbol = symbol,
                    securityName = positions.firstOrNull()?.securityName,
                    currentValue = positions.sumOf { it.currentValue ?: BigDecimal.ZERO },
                    currency = positions.firstOrNull()?.currency
                )
            }

        // Calculate accuracy: 100 - mean(|drift|) for all targets, clamped >= 0
        val accuracy = calculateAccuracyFromHoldings(holdings)

        return DriftAnalysisResponse(
            groupId = groupId,
            groupName = group.name,
            accuracy = accuracy,
            totalValue = totalValue,
            cash = totalCash,
            holdings = holdings,
            excludedAssets = excludedAssetDtos,
            newAssets = newAssets
        )
    }

    fun calculateAccuracy(groupId: Long): BigDecimal {
        val linkedAccounts = groupAccountRepository.findByGroupId(groupId)
        if (linkedAccounts.isEmpty()) return BigDecimal.ZERO

        val targets = targetRepository.findByGroupId(groupId)
        if (targets.isEmpty()) return BigDecimal.ZERO

        val excludedSymbols = excludedAssetRepository.findByGroupId(groupId).map { it.symbol }.toSet()

        val allPositions = linkedAccounts.flatMap { link ->
            positionRepository.findCurrentPositionsByConnectionId(link.connection.id)
        }.filter { it.symbol !in excludedSymbols }

        val totalCashValue = linkedAccounts.sumOf { link ->
            getCashTotal(link.connection.id)
        }

        val totalPositionValue = allPositions.sumOf { it.currentValue ?: BigDecimal.ZERO }
        val totalValue = totalPositionValue + totalCashValue

        if (totalValue <= BigDecimal.ZERO) return BigDecimal.ZERO

        val positionsBySymbol = allPositions.groupBy { it.symbol }
        val drifts = targets.map { target ->
            val actualValue = positionsBySymbol[target.symbol]?.sumOf { it.currentValue ?: BigDecimal.ZERO } ?: BigDecimal.ZERO
            val actualPercent = actualValue.multiply(BigDecimal(100)).divide(totalValue, 4, RoundingMode.HALF_UP)
            (actualPercent - target.targetPercent).abs()
        }

        val meanDrift = if (drifts.isNotEmpty()) {
            drifts.fold(BigDecimal.ZERO) { acc, d -> acc + d }
                .divide(BigDecimal(drifts.size), 4, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return (BigDecimal(100) - meanDrift).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
    }

    fun calculateTotalValue(groupId: Long): BigDecimal {
        val linkedAccounts = groupAccountRepository.findByGroupId(groupId)
        val positionValue = linkedAccounts.sumOf { link ->
            positionRepository.sumCurrentValueByConnectionId(link.connection.id) ?: BigDecimal.ZERO
        }
        val cashValue = linkedAccounts.sumOf { link ->
            getCashTotal(link.connection.id)
        }
        return positionValue + cashValue
    }

    private fun calculateAccuracyFromHoldings(holdings: List<DriftHoldingDto>): BigDecimal {
        if (holdings.isEmpty()) return BigDecimal.ZERO
        val meanDrift = holdings.map { it.driftPercent.abs() }
            .fold(BigDecimal.ZERO) { acc, d -> acc + d }
            .divide(BigDecimal(holdings.size), 4, RoundingMode.HALF_UP)
        return (BigDecimal(100) - meanDrift).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
    }

    private fun getCashTotal(connectionId: Long): BigDecimal {
        val latestBalance = balanceRepository.findLatestByConnectionId(connectionId) ?: return BigDecimal.ZERO
        if (latestBalance.cash == null) return BigDecimal.ZERO
        return try {
            val cashMap = parseCashJson(latestBalance.cash)
            cashMap.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    private fun parseCashJson(cashJson: String): Map<String, BigDecimal> {
        val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<Map<String, BigDecimal>>() {}
        return objectMapper.readValue(cashJson, typeRef)
    }
}
