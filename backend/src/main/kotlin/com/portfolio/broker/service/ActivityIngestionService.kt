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
    private val objectMapper: ObjectMapper
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
            val externalId = activity.id?.toString()
            if (externalId != null) {
                val existing = activityRepository.findByConnectionIdAndExternalId(connectionId, externalId)
                if (existing != null) continue
            }

            val tradeDate = try {
                activity.tradeDate?.let { LocalDate.parse(it.toString().take(10)) }
            } catch (e: Exception) {
                null
            } ?: continue

            val entity = BrokerActivity(
                connection = connection,
                externalId = externalId,
                type = mapActivityType(activity.type),
                symbol = activity.symbol?.symbol,
                description = activity.description,
                quantity = activity.units?.let { BigDecimal(it.toString()) },
                price = activity.price?.let { BigDecimal(it.toString()) },
                amount = activity.amount?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO,
                fee = activity.fee?.let { BigDecimal(it.toString()) },
                currency = activity.currency?.code ?: "CAD",
                tradeDate = tradeDate,
                settlementDate = activity.settlementDate?.let {
                    try { LocalDate.parse(it.toString().take(10)) } catch (e: Exception) { null }
                },
                accountName = connection.accountName,
                optionType = activity.optionType,
                rawPayload = try { objectMapper.writeValueAsString(activity) } catch (e: Exception) { null }
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
        for (balance in balances) {
            val curr = balance.currency?.code ?: "CAD"
            val amount = balance.cash?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO
            cashMap[curr] = (cashMap[curr] ?: BigDecimal.ZERO) + amount
        }

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
            cash = objectMapper.writeValueAsString(cashMap),
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

    private fun mapActivityType(type: String?): String {
        return when (type?.uppercase()) {
            "BUY" -> "BUY"
            "SELL" -> "SELL"
            "DIVIDEND" -> "DIVIDEND"
            "CONTRIBUTION", "DEPOSIT", "TRANSFER_IN" -> "TRANSFER_IN"
            "WITHDRAWAL", "TRANSFER_OUT" -> "TRANSFER_OUT"
            "FEE", "COMMISSION" -> "FEE"
            "INTEREST" -> "INTEREST"
            "OPTIONEXPIRATION", "OPTION_EXPIRATION" -> "OPTIONEXPIRATION"
            "OPTIONASSIGNMENT", "OPTION_ASSIGNMENT" -> "OPTIONASSIGNMENT"
            "OPTIONEXERCISE", "OPTION_EXERCISE" -> "OPTIONEXERCISE"
            else -> type?.uppercase() ?: "OTHER"
        }
    }
}
