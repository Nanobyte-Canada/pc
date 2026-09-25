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
import org.springframework.transaction.support.TransactionOperations
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class ActivityIngestionService(
    private val connectionRepository: BrokerConnectionRepository,
    private val activityRepository: BrokerActivityRepository,
    private val balanceRepository: BrokerBalanceRepository,
    private val gatewayClient: BrokerGatewayClient,
    private val objectMapper: ObjectMapper,
    private val exchangeRateService: ExchangeRateService,
    private val transactionOperations: TransactionOperations,
    private val progressService: BrokerSyncProgressService,
    private val syncGuard: ConnectionSyncGuard,
    @Value("\${broker.sync.max-lookback-years:5}")
    private val maxLookbackYears: Int = 5,
    @Value("\${broker.sync.chunk-days:29}")
    private val chunkDays: Int = 29
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Broker days are ET days: the walk floor, the newest chunk and the balance as-of date. */
    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Toronto")
    }

    /**
     * Non-transactional entry point: each chunk of the full-history sync commits in its own
     * transaction (see [syncFullHistory]) so a failure loses at most the chunk in flight.
     *
     * Single-flight per connection: scheduled, manual and dashboard runs all converge here,
     * so an overlapping run for the same connection is skipped rather than queued.
     */
    fun syncActivitiesForConnection(connectionId: Long): Int {
        if (!syncGuard.tryAcquire(connectionId)) {
            log.info("Sync already in progress for connection {}; skipping", connectionId)
            return 0
        }
        try {
            return doSyncActivitiesForConnection(connectionId)
        } finally {
            syncGuard.release(connectionId)
        }
    }

    private fun doSyncActivitiesForConnection(connectionId: Long): Int {
        val connection = connectionRepository.findById(connectionId).orElseThrow {
            IllegalArgumentException("Connection not found: $connectionId")
        }

        val latestDate = activityRepository.findLatestTradeDateByConnectionId(connectionId)

        // A stored full-history progress row means an interrupted backfill is still owed. The
        // incremental path only fetches the recent window, so dispatching on latestDate alone
        // would leave the historical gap (up to maxLookbackYears) unfilled forever. A completed
        // walk deletes its row (clear), and a row that survived a crash after the final advance
        // sits below the lookback floor, so syncFullHistory falls straight through and clears it.
        val resumeFullHistory =
            progressService.get(connectionId, BrokerSyncProgressService.ACTIVITIES_FULL) != null

        val insertedCount = try {
            val gwConnId = connection.gatewayConnectionId
            val accountId = connection.accountIdExternal
            if (latestDate == null || resumeFullHistory) {
                when {
                    gwConnId == null -> {
                        log.warn("Connection {} has no gateway connection ID, skipping historical sync", connectionId)
                        0
                    }
                    accountId == null -> {
                        log.warn("Connection {} has no external account ID, skipping historical sync", connectionId)
                        0
                    }
                    else -> {
                        if (resumeFullHistory) {
                            log.info("Resuming interrupted full historical sync for connection {} (user {})",
                                connectionId, connection.user.id)
                        } else {
                            log.info("No existing activities for connection {} (user {}), starting full " +
                                "historical sync (lookback={}y)", connectionId, connection.user.id, maxLookbackYears)
                        }
                        syncFullHistory(connection.id, gwConnId, accountId)
                    }
                }
            } else {
                // Incremental sync from latest known date
                val startDate = latestDate.minusDays(1)
                log.info("Incremental sync for connection {} (user {}), startDate={}",
                    connectionId, connection.user.id, startDate)
                syncIncremental(connection, startDate)
            }
        } catch (e: SyncInterruptedException) {
            // Scheduled runs stay resilient: report what was ingested so far and let the stored
            // progress row drive the next run's resume point.
            log.warn("Full history sync for connection {} stopped after {} new activities; " +
                "progress saved for resume", connectionId, e.insertedSoFar)
            recordActivitiesOutcome(connection, e.insertedSoFar, e)
            return e.insertedSoFar
        } catch (e: Exception) {
            // Incremental fetch/persist failure — record the outcome first so the status is
            // honest for this path too, then propagate so callers still see the error.
            recordActivitiesOutcome(connection, 0, e)
            throw e
        }

        recordActivitiesOutcome(connection, insertedCount, null)

        log.info("Synced {} new activities for connection {}", insertedCount, connectionId)
        return insertedCount
    }

    /**
     * Records the honest outcome of an activities sync. The watermark only advances on a clean
     * run — a PARTIAL or FAILED sync leaves it at the last genuine success — while the status is
     * written on every path so the API and UI can show staleness truthfully.
     */
    private fun recordActivitiesOutcome(
        connection: BrokerConnection,
        inserted: Int,
        failure: Throwable?
    ) {
        val status = when {
            failure == null -> "SUCCESS"
            inserted > 0 -> "PARTIAL"
            else -> "FAILED"
        }
        if (status == "SUCCESS") connection.lastActivitiesFetchedAt = OffsetDateTime.now()
        connection.lastActivitiesSyncStatus = status
        connectionRepository.save(connection)
    }

    /**
     * Walks backward from now (or from stored progress) one chunk at a time, committing each
     * chunk — fetched activities + the advanced progress row — in a single transaction. The
     * broker HTTP call happens outside the transaction so no connection is held across it.
     *
     * Iteration must stay backward: the loop stops after 12 consecutive empty chunks, which
     * only happen once the account's retention horizon is reached when walking toward the past.
     */
    internal fun syncFullHistory(connectionId: Long, gwConnId: String, accountId: String): Int {
        val earliest = LocalDate.now(ZONE).minusYears(maxLookbackYears.toLong())
        val resumeFrom = progressService.get(connectionId, BrokerSyncProgressService.ACTIVITIES_FULL)?.nextChunkEnd
        var chunkEnd = resumeFrom ?: LocalDate.now(ZONE)
        var totalInserted = 0
        var emptyChunksInRow = 0

        log.info("Full historical sync for connection {}: {} to {} in {}-day chunks{}",
            connectionId, earliest, chunkEnd, chunkDays,
            if (resumeFrom != null) " (resuming from $resumeFrom)" else "")

        while (!chunkEnd.isBefore(earliest)) {
            val chunkStart = maxOf(chunkEnd.minusDays((chunkDays - 1).toLong()), earliest)

            val inserted = try {
                val response = gatewayClient.getActivities(gwConnId, accountId, chunkStart, chunkEnd)
                val activities = response.path("activities")
                val count = transactionOperations.execute<Int> {
                    val connection = connectionRepository.findById(connectionId).orElseThrow()
                    val saved = processAndSaveActivities(activities, connection)
                    progressService.advance(
                        connectionId, BrokerSyncProgressService.ACTIVITIES_FULL, chunkStart.minusDays(1)
                    )
                    saved
                } ?: 0
                if (activities.size() > 0) {
                    log.info("Chunk {}..{}: {} fetched, {} new for connection {}",
                        chunkStart, chunkEnd, activities.size(), count, connectionId)
                }
                count
            } catch (e: Exception) {
                log.warn("Activity chunk {}..{} failed for connection {}; resuming from saved progress next run",
                    chunkStart, chunkEnd, connectionId, e)
                throw SyncInterruptedException(totalInserted)
            }

            totalInserted += inserted
            emptyChunksInRow = if (inserted == 0) emptyChunksInRow + 1 else 0
            if (emptyChunksInRow >= 12) {
                log.info("Stopping historical sync for connection {} — {} consecutive empty chunks " +
                    "(reached account start)", connectionId, emptyChunksInRow)
                break
            }
            chunkEnd = chunkStart.minusDays(1)
        }

        progressService.clear(connectionId, BrokerSyncProgressService.ACTIVITIES_FULL)
        log.info("Historical sync complete for connection {}: {} new activities", connectionId, totalInserted)
        return totalInserted
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

        // The fetch above is deliberately outside the transaction; persist in its own.
        return transactionOperations.execute {
            val conn = connectionRepository.findById(connection.id).orElseThrow()
            processAndSaveActivities(activitiesJson.path("activities"), conn)
        } ?: 0
    }

    private fun processAndSaveActivities(activities: JsonNode, connection: BrokerConnection): Int {
        var insertedCount = 0
        for (activity in activities) {
            val tradeDate = parseJsonLocalDate(activity.path("tradeDate")) ?: continue

            val rawAmount = if (activity.has("amount") && !activity.path("amount").isNull)
                BigDecimal(activity.path("amount").asText()) else BigDecimal.ZERO
            val currency = activity.path("currency").asText("CAD")
            val type = activity.path("type").asText("OTHER")
            val symbol = activity.path("symbol").asText(null)
            val description = activity.path("description").asText(null)
            val quantity = if (activity.has("quantity") && !activity.path("quantity").isNull)
                BigDecimal(activity.path("quantity").asText()) else null
            val price = if (activity.has("price") && !activity.path("price").isNull)
                BigDecimal(activity.path("price").asText()) else null
            val fee = if (activity.has("fee") && !activity.path("fee").isNull)
                BigDecimal(activity.path("fee").asText()) else null
            val settlementDate = parseJsonLocalDate(activity.path("settlementDate"))
            val optionType = activity.path("optionType").asText(null)

            // Questrade activities carry no broker-assigned id; derive a stable fingerprint so
            // re-syncing overlapping windows stays idempotent. Wealthsimple supplies a
            // canonicalId as externalId and keeps using it directly.
            val key = activity.path("externalId").asText(null)
                ?: ActivityFingerprint.of(
                    type, symbol, description, quantity, price, rawAmount, fee,
                    currency, tradeDate, settlementDate, optionType
                )

            if (activityRepository.findByConnectionIdAndExternalId(connection.id, key) != null) continue

            val (amountCad, exchangeRate) = computeCadAmount(rawAmount, currency, tradeDate, type)

            val entity = BrokerActivity(
                connection = connection,
                externalId = key,
                type = type,
                symbol = symbol,
                description = description,
                quantity = quantity,
                price = price,
                amount = rawAmount,
                fee = fee,
                currency = currency,
                tradeDate = tradeDate,
                settlementDate = settlementDate,
                accountName = connection.accountName,
                optionType = optionType,
                amountCad = amountCad,
                exchangeRate = exchangeRate,
                rawPayload = objectMapper.writeValueAsString(activity)
            )
            activityRepository.save(entity)
            insertedCount++
        }
        return insertedCount
    }

    /**
     * Non-transactional wrapper around [syncBalanceInTransaction], which runs in its own
     * transaction. A failure rolls that transaction back, so a FAILED status written *inside* it
     * (what the old `@Transactional(REQUIRES_NEW)` version did) was discarded for every caller
     * that went through the transaction proxy — `BrokerController.sync-all` — while the
     * scheduler's self-invocation path, which never got a proxy, accidentally kept it. The status
     * is now written after the rollback, in its own small transaction, and the original exception
     * is rethrown unchanged so callers still see the failure.
     */
    fun syncBalanceForConnection(connectionId: Long) {
        if (!syncGuard.tryAcquire(connectionId)) {
            log.info("Sync already in progress for connection {}; skipping", connectionId)
            return
        }
        try {
            transactionOperations.execute<Boolean> {
                syncBalanceInTransaction(connectionId)
                true
            }
        } catch (e: Exception) {
            recordBalanceFailure(connectionId)
            throw e
        } finally {
            syncGuard.release(connectionId)
        }
    }

    /**
     * Records the FAILED balance status in its own transaction, outside the balance transaction
     * that just rolled back, so the write commits. Never throws: a failure to record is logged
     * because the original sync exception must reach the caller untouched.
     */
    private fun recordBalanceFailure(connectionId: Long) {
        try {
            transactionOperations.execute<Boolean> {
                connectionRepository.findById(connectionId).ifPresent { conn ->
                    conn.lastBalanceSyncStatus = "FAILED"
                    connectionRepository.save(conn)
                }
                true
            }
        } catch (e: Exception) {
            log.error("Failed to record FAILED balance status for connection {}", connectionId, e)
        }
    }

    private fun syncBalanceInTransaction(connectionId: Long) {
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

        val today = LocalDate.now(ZONE)
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
        connection.lastBalanceSyncStatus = "SUCCESS"
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
    private fun parseJsonLocalDate(node: JsonNode): LocalDate? {
        if (node.isMissingNode || node.isNull) return null
        if (node.isArray && node.size() >= 3) {
            return try {
                LocalDate.of(node[0].asInt(), node[1].asInt(), node[2].asInt())
            } catch (_: Exception) { null }
        }
        val text = node.asText(null) ?: return null
        return try {
            LocalDate.parse(text.substring(0, minOf(text.length, 10)))
        } catch (_: Exception) { null }
    }

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

/**
 * A full-history chunk failed; the sync stops but the progress row survives, so the next run
 * resumes at the failed chunk. Carries the activities already committed so callers can report
 * a partial sync instead of failing the whole run.
 */
class SyncInterruptedException(val insertedSoFar: Int) : RuntimeException()
