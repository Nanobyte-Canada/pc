package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.repository.UserRepository
import com.portfolio.broker.entity.BrokerActivity
import com.portfolio.broker.entity.BrokerBalanceSnapshot
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class ActivityIngestionService(
    private val connectionRepository: BrokerConnectionRepository,
    private val activityRepository: BrokerActivityRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val snapTradeService: SnapTradeService,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val exchangeRateService: ExchangeRateService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun syncActivitiesForConnection(connectionId: Long): Int {
        val connection = connectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }
        val user = connection.user

        // Incremental sync: find latest trade_date in DB
        val latestDate = activityRepository.findLatestTradeDateByConnectionId(connectionId)
        val startDate = latestDate?.minusDays(1) // overlap by 1 day for safety

        log.info("Syncing activities for connection {} (user {}), startDate={}",
            connectionId, user.id, startDate ?: "all-time")

        val activities = try {
            snapTradeService.getActivities(
                user = user,
                startDate = startDate,
                accounts = connection.accountIdExternal
            )
        } catch (e: Exception) {
            log.error("Failed to fetch activities for connection {}: {}", connectionId, e.message)
            throw e
        }

        var insertedCount = 0
        for (activity in activities) {
            val externalId = activity.id
            if (externalId != null) {
                val existing = activityRepository.findByConnectionIdAndExternalId(connectionId, externalId)
                if (existing != null) continue
            }

            val tradeDate = activity.tradeDate ?: continue

            val rawAmount = activity.amount?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            val currency = activity.currency ?: "CAD"
            val mappedType = mapActivityType(activity.type)

            // Compute CAD-equivalent amount for reporting
            val (amountCad, exchangeRate) = computeCadAmount(rawAmount, currency, tradeDate, mappedType)

            val entity = BrokerActivity(
                connection = connection,
                externalId = externalId,
                type = mappedType,
                symbol = activity.symbol,
                description = activity.description,
                quantity = activity.units?.let { BigDecimal(it.toString()) },
                price = activity.price?.let { BigDecimal(it.toString()) },
                amount = rawAmount,
                fee = activity.fee?.let { BigDecimal(it.toString()) },
                currency = currency,
                tradeDate = tradeDate,
                settlementDate = activity.settlementDate,
                accountName = connection.accountName,
                optionType = activity.optionType,
                amountCad = amountCad,
                exchangeRate = exchangeRate,
                rawPayload = activity.rawJson
            )
            activityRepository.save(entity)
            insertedCount++
        }

        connection.lastActivitiesFetchedAt = OffsetDateTime.now()
        connectionRepository.save(connection)

        log.info("Synced {} new activities for connection {} (total fetched: {})",
            insertedCount, connectionId, activities.size)
        return insertedCount
    }

    @Transactional
    fun syncBalanceForConnection(connectionId: Long) {
        val connection = connectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }
        val user = connection.user
        val accountId = connection.accountIdExternal ?: return

        log.info("Syncing balance for connection {} (user {})", connectionId, user.id)

        val balances = try {
            snapTradeService.getAccountBalance(user, accountId)
        } catch (e: Exception) {
            log.error("Failed to fetch balance for connection {}: {}", connectionId, e.message)
            throw e
        }

        val today = LocalDate.now()
        val cashMap = mutableMapOf<String, BigDecimal>()
        val buyingPowerMap = mutableMapOf<String, BigDecimal>()
        for (balance in balances) {
            val curr = balance.currency ?: "CAD"
            val amount = balance.cash?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            cashMap["cash_$curr"] = (cashMap["cash_$curr"] ?: BigDecimal.ZERO) + amount

            val bp = balance.buyingPower?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            if (bp > BigDecimal.ZERO) {
                buyingPowerMap["buying_power_$curr"] = (buyingPowerMap["buying_power_$curr"] ?: BigDecimal.ZERO) + bp
            }
        }
        val combined = cashMap + buyingPowerMap

        val totalValue = connection.totalValue

        val existing = balanceRepository.findByConnectionIdAndAsOfDate(connectionId, today)
        if (existing != null) {
            // Update in place by deleting and re-inserting (immutable entity pattern)
            balanceRepository.delete(existing)
            balanceRepository.flush()
        }

        val snapshot = BrokerBalanceSnapshot(
            connection = connection,
            totalValue = totalValue,
            cash = objectMapper.writeValueAsString(combined),
            currency = "CAD",
            asOfDate = today
        )
        balanceRepository.save(snapshot)

        connection.lastBalanceFetchedAt = OffsetDateTime.now()
        connectionRepository.save(connection)

        log.info("Balance snapshot saved for connection {} as of {}", connectionId, today)
    }

    fun syncAllConnections() {
        val connections = connectionRepository.findAll()
            .filter { it.status != ConnectionStatus.DISCONNECTED }

        log.info("Starting activity/balance sync for {} non-disconnected connections", connections.size)

        var syncedCount = 0
        var activitiesAdded = 0
        var balanceSnapshots = 0

        for (connection in connections) {
            try {
                val added = syncActivitiesForConnection(connection.id)
                activitiesAdded += added
                syncBalanceForConnection(connection.id)
                balanceSnapshots++
                syncedCount++
            } catch (e: Exception) {
                log.error("Failed to sync connection {}: {}", connection.id, e.message, e)
            }
        }

        log.info("Sync complete: synced {} of {} connections, {} activities added, {} balance snapshots created",
            syncedCount, connections.size, activitiesAdded, balanceSnapshots)
    }

    /**
     * Maps raw SnapTrade activity type strings to normalized types.
     *
     * SnapTrade may send plural forms (e.g. "TRANSFERS", "WITHDRAWALS") or
     * various synonyms. This function normalizes them to a consistent set:
     * - TRANSFER_IN: contributions, deposits, transfers in
     * - TRANSFER_OUT: withdrawals, transfers out
     */
    private fun mapActivityType(type: String?): String {
        return when (type?.uppercase()) {
            "BUY" -> "BUY"
            "SELL" -> "SELL"
            "DIVIDEND" -> "DIVIDEND"
            "CONTRIBUTION", "DEPOSIT", "TRANSFER_IN", "TRANSFERS" -> "TRANSFER_IN"
            "WITHDRAWAL", "TRANSFER_OUT", "WITHDRAWALS" -> "TRANSFER_OUT"
            "FEE" -> "FEE"
            "COMMISSION" -> "COMMISSION"
            "INTEREST" -> "INTEREST"
            "OPTIONEXPIRATION", "OPTION_EXPIRATION" -> "OPTIONEXPIRATION"
            "OPTIONASSIGNMENT", "OPTION_ASSIGNMENT" -> "OPTIONASSIGNMENT"
            "OPTIONEXERCISE", "OPTION_EXERCISE" -> "OPTIONEXERCISE"
            else -> type?.uppercase() ?: "OTHER"
        }
    }

    /**
     * Computes the CAD-equivalent amount and the exchange rate used.
     *
     * For CAD amounts or zero amounts, no FX lookup is performed.
     * For non-CAD, calls [ExchangeRateService] and falls back to the raw amount if unavailable.
     */
    private fun computeCadAmount(
        amount: BigDecimal,
        currency: String,
        tradeDate: LocalDate,
        type: String
    ): Pair<BigDecimal, BigDecimal?> {
        if (currency.uppercase() == "CAD") {
            return amount to BigDecimal.ONE
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO to null
        }

        val rate = exchangeRateService.getRate(currency, tradeDate)
        return if (rate != null) {
            amount.multiply(rate) to rate
        } else {
            log.warn("No exchange rate for {} on {}, using raw amount as CAD fallback (type={})",
                currency, tradeDate, type)
            amount to null
        }
    }
}
