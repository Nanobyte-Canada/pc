package com.portfolio.broker.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.client.BrokerGatewayClient
import com.portfolio.broker.entity.BrokerActivity
import com.portfolio.broker.entity.BrokerBalanceSnapshot
import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.ConnectionStatus
import com.portfolio.broker.repository.BrokerActivityRepository
import com.portfolio.broker.repository.BrokerBalanceRepository
import com.portfolio.broker.repository.BrokerConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class ActivityIngestionService(
    private val connectionRepository: BrokerConnectionRepository,
    private val activityRepository: BrokerActivityRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val gatewayClient: BrokerGatewayClient,
    private val objectMapper: ObjectMapper,
    private val exchangeRateService: ExchangeRateService,
    @Value("\${broker.sync.max-lookback-years:25}")
    private val maxLookbackYears: Int = 25
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun syncActivitiesForConnection(connectionId: Long): Int {
        val connection = connectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }

        val latestDate = activityRepository.findLatestTradeDateByConnectionId(connectionId)

        val insertedCount = if (latestDate == null) {
            // No activities exist — full historical sync
            log.info("No existing activities for connection {} (user {}), starting full historical sync " +
                "(lookback={}y)", connectionId, connection.user.id, maxLookbackYears)
            syncFullHistory(connection)
        } else {
            // Incremental sync from latest known date
            val startDate = latestDate.minusDays(1)
            log.info("Incremental sync for connection {} (user {}), startDate={}",
                connectionId, connection.user.id, startDate)
            syncIncremental(connection, startDate)
        }

        connection.lastActivitiesFetchedAt = OffsetDateTime.now()
        connectionRepository.save(connection)

        log.info("Synced {} new activities for connection {}", insertedCount, connectionId)
        return insertedCount
    }

    private fun syncFullHistory(connection: BrokerConnection): Int {
        val startDate = LocalDate.now().minusYears(maxLookbackYears.toLong())
        val endDate = LocalDate.now()
        val gwConnId = connection.gatewayConnectionId
            ?: run {
                log.warn("Connection {} has no gateway connection ID, skipping historical sync", connection.id)
                return 0
            }
        val accountId = connection.accountIdExternal
            ?: run {
                log.warn("Connection {} has no external account ID, skipping historical sync", connection.id)
                return 0
            }

        log.info("Full historical sync for connection {}: fetching {} to {}", connection.id, startDate, endDate)

        val activitiesJson = try {
            gatewayClient.getActivities(gwConnId, accountId, startDate, endDate)
        } catch (e: Exception) {
            log.error("Historical sync failed for connection {}: {}", connection.id, e.message)
            return 0
        }

        val activities = activitiesJson.path("activities")
        val inserted = processAndSaveActivities(activities, connection)

        log.info("Historical sync complete for connection {}: {} activities ({} new)",
            connection.id, activities.size(), inserted)

        return inserted
    }

    private fun syncIncremental(connection: BrokerConnection, startDate: LocalDate): Int {
        val gwConnId = connection.gatewayConnectionId
            ?: run {
                log.warn("Connection {} has no gateway connection ID, skipping incremental sync", connection.id)
                return 0
            }
        val accountId = connection.accountIdExternal
            ?: run {
                log.warn("Connection {} has no external account ID, skipping incremental sync", connection.id)
                return 0
            }

        val activitiesJson = try {
            gatewayClient.getActivities(gwConnId, accountId, startDate, null)
        } catch (e: Exception) {
            log.error("Failed to fetch activities for connection {}: {}", connection.id, e.message)
            throw e
        }

        val activities = activitiesJson.path("activities")
        return processAndSaveActivities(activities, connection)
    }

    private fun processAndSaveActivities(activities: JsonNode, connection: BrokerConnection): Int {
        var insertedCount = 0
        for (activity in activities) {
            val externalId = activity.path("externalId").asText(null)
            if (externalId != null) {
                val existing = activityRepository.findByConnectionIdAndExternalId(connection.id, externalId)
                if (existing != null) continue
            }

            val tradeDateStr = activity.path("tradeDate").asText(null) ?: continue
            val tradeDate = LocalDate.parse(tradeDateStr)

            val rawAmount = if (activity.has("amount") && !activity.path("amount").isNull)
                BigDecimal(activity.path("amount").asText()) else BigDecimal.ZERO
            val currency = activity.path("currency").asText("CAD")
            val type = activity.path("type").asText("OTHER")

            val (amountCad, exchangeRate) = computeCadAmount(rawAmount, currency, tradeDate, type)

            val settlementDateStr = activity.path("settlementDate").asText(null)
            val settlementDate = settlementDateStr?.let { LocalDate.parse(it) }

            val quantity = if (activity.has("quantity") && !activity.path("quantity").isNull)
                BigDecimal(activity.path("quantity").asText()) else null
            val price = if (activity.has("price") && !activity.path("price").isNull)
                BigDecimal(activity.path("price").asText()) else null
            val fee = if (activity.has("fee") && !activity.path("fee").isNull)
                BigDecimal(activity.path("fee").asText()) else null

            val entity = BrokerActivity(
                connection = connection,
                externalId = externalId,
                type = type,
                symbol = activity.path("symbol").asText(null),
                description = activity.path("description").asText(null),
                quantity = quantity,
                price = price,
                amount = rawAmount,
                fee = fee,
                currency = currency,
                tradeDate = tradeDate,
                settlementDate = settlementDate,
                accountName = connection.accountName,
                optionType = activity.path("optionType").asText(null),
                amountCad = amountCad,
                exchangeRate = exchangeRate,
                rawPayload = objectMapper.writeValueAsString(activity)
            )
            activityRepository.save(entity)
            insertedCount++
        }
        return insertedCount
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun syncBalanceForConnection(connectionId: Long) {
        val connection = connectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }
        val gwConnId = connection.gatewayConnectionId ?: return
        val accountId = connection.accountIdExternal ?: return

        log.info("Syncing balance for connection {} (user {})", connectionId, connection.user.id)

        val balanceJson = try {
            gatewayClient.getBalances(gwConnId, accountId)
        } catch (e: Exception) {
            log.error("Failed to fetch balance for connection {}: {}", connectionId, e.message)
            throw e
        }

        val today = LocalDate.now()
        val cashMap = mutableMapOf<String, BigDecimal>()
        val buyingPowerMap = mutableMapOf<String, BigDecimal>()

        val cashBalances = balanceJson.path("cashBalances")
        for (cb in cashBalances) {
            val curr = cb.path("currency").asText("CAD")
            val amount = if (cb.has("amount") && !cb.path("amount").isNull)
                BigDecimal(cb.path("amount").asText()) else BigDecimal.ZERO
            cashMap["cash_$curr"] = (cashMap["cash_$curr"] ?: BigDecimal.ZERO) + amount
        }

        val buyingPower = if (balanceJson.has("buyingPower") && !balanceJson.path("buyingPower").isNull)
            BigDecimal(balanceJson.path("buyingPower").asText()) else BigDecimal.ZERO
        val balanceCurrency = balanceJson.path("currency").asText("CAD")
        if (buyingPower > BigDecimal.ZERO) {
            buyingPowerMap["buying_power_$balanceCurrency"] = buyingPower
        }

        val combined = cashMap + buyingPowerMap

        val totalValue = if (balanceJson.has("totalValue") && !balanceJson.path("totalValue").isNull)
            BigDecimal(balanceJson.path("totalValue").asText()) else connection.totalValue

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
