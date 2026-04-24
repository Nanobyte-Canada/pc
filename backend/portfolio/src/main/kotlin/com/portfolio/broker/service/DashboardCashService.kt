package com.portfolio.broker.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.dto.CurrencyAmountDto
import com.portfolio.broker.dto.DashboardCashResponse
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class DashboardCashService(
    private val connectionRepository: BrokerConnectionRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val exchangeRateService: ExchangeRateService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cashTypeRef = object : TypeReference<Map<String, BigDecimal>>() {}

    fun getCash(userId: Long, connectionId: Long? = null): DashboardCashResponse {
        val connectionIds = getConnectionIds(userId, connectionId)
        if (connectionIds.isEmpty()) {
            return DashboardCashResponse(emptyList(), emptyList(), BigDecimal.ZERO, BigDecimal.ZERO)
        }

        val cashByCurrency = mutableMapOf<String, BigDecimal>()
        val buyingPowerByCurrency = mutableMapOf<String, BigDecimal>()

        for (connId in connectionIds) {
            val snapshot = balanceRepository.findLatestByConnectionId(connId) ?: continue
            val cashJson = snapshot.cash ?: continue

            try {
                val parsed = objectMapper.readValue(cashJson, cashTypeRef)
                // Cash and buying_power are stored in the JSONB
                for ((key, value) in parsed) {
                    if (key.startsWith("buying_power_")) {
                        val currency = key.removePrefix("buying_power_").uppercase()
                        buyingPowerByCurrency[currency] = (buyingPowerByCurrency[currency] ?: BigDecimal.ZERO) + value
                    } else if (key.startsWith("cash_")) {
                        val currency = key.removePrefix("cash_").uppercase()
                        cashByCurrency[currency] = (cashByCurrency[currency] ?: BigDecimal.ZERO) + value
                    } else if (key.length == 3 && key == key.uppercase()) {
                        // Simple currency code key (e.g., "CAD": 5000)
                        cashByCurrency[key] = (cashByCurrency[key] ?: BigDecimal.ZERO) + value
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to parse cash JSONB for connection {}", connId, e)
            }
        }

        val today = LocalDate.now()
        val totalCashCAD = cashByCurrency.entries.fold(BigDecimal.ZERO) { acc, (currency, amount) ->
            val rate = exchangeRateService.getRate(currency, today) ?: BigDecimal.ONE
            acc + amount * rate
        }
        val totalBuyingPowerCAD = buyingPowerByCurrency.entries.fold(BigDecimal.ZERO) { acc, (currency, amount) ->
            val rate = exchangeRateService.getRate(currency, today) ?: BigDecimal.ONE
            acc + amount * rate
        }

        return DashboardCashResponse(
            availableCash = cashByCurrency.map { CurrencyAmountDto(it.key, it.value.setScale(2, RoundingMode.HALF_UP)) }
                .sortedByDescending { it.amount },
            buyingPower = buyingPowerByCurrency.map { CurrencyAmountDto(it.key, it.value.setScale(2, RoundingMode.HALF_UP)) }
                .sortedByDescending { it.amount },
            totalCashCAD = totalCashCAD.setScale(2, RoundingMode.HALF_UP),
            totalBuyingPowerCAD = totalBuyingPowerCAD.setScale(2, RoundingMode.HALF_UP)
        )
    }

    fun getTotalCashFromSnapshot(connId: Long): BigDecimal {
        val snapshot = balanceRepository.findLatestByConnectionId(connId) ?: return BigDecimal.ZERO
        val cashJson = snapshot.cash ?: return BigDecimal.ZERO
        return try {
            val parsed = objectMapper.readValue(cashJson, cashTypeRef)
            val today = LocalDate.now()
            parsed.entries
                .filter { !it.key.startsWith("buying_power_") }
                .fold(BigDecimal.ZERO) { acc, (key, value) ->
                    val currency = when {
                        key.startsWith("cash_") -> key.removePrefix("cash_").uppercase()
                        key.length == 3 && key == key.uppercase() -> key
                        else -> return@fold acc
                    }
                    val rate = exchangeRateService.getRate(currency, today) ?: BigDecimal.ONE
                    acc + value * rate
                }
        } catch (e: Exception) {
            log.warn("Failed to parse cash for connection {}", connId, e)
            BigDecimal.ZERO
        }
    }

    fun getBuyingPowerFromSnapshot(connId: Long): BigDecimal? {
        val snapshot = balanceRepository.findLatestByConnectionId(connId) ?: return null
        val cashJson = snapshot.cash ?: return null
        return try {
            val parsed = objectMapper.readValue(cashJson, cashTypeRef)
            val today = LocalDate.now()
            val bpEntries = parsed.entries.filter { it.key.startsWith("buying_power_") }
            if (bpEntries.isEmpty()) return null
            bpEntries.fold(BigDecimal.ZERO) { acc, (key, value) ->
                val currency = key.removePrefix("buying_power_").uppercase()
                val rate = exchangeRateService.getRate(currency, today) ?: BigDecimal.ONE
                acc + value * rate
            }
        } catch (e: Exception) {
            log.warn("Failed to parse buying power for connection {}", connId, e)
            null
        }
    }

    private fun getConnectionIds(userId: Long, connectionId: Long?): List<Long> {
        return if (connectionId != null) {
            listOf(connectionId)
        } else {
            connectionRepository.findByUserIdAndStatus(userId, ConnectionStatus.ACTIVE).map { it.id }
        }
    }
}
