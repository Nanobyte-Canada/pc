package com.portfolio.broker.service

import java.math.BigDecimal
import java.time.LocalDate

data class ActivityRowForBackfill(
    val id: Long,
    val connectionId: Long,
    val type: String,
    val symbol: String?,
    val description: String?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val currency: String?,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate?,
    val optionType: String?
)

/**
 * Computes the fingerprint backfill for rows that predate the dedup key. Rows are grouped
 * per (connection, fingerprint); the earliest id is kept and the rest are deleted.
 */
object ActivityBackfillPlanner {

    data class BackfillPlan(
        val fingerprintById: Map<Long, String>,
        val duplicateIdsToDelete: List<Long>
    )

    fun plan(rows: List<ActivityRowForBackfill>): BackfillPlan {
        val keptByKey = LinkedHashMap<Pair<Long, String>, Long>()
        val fingerprintById = LinkedHashMap<Long, String>()
        val duplicateIds = mutableListOf<Long>()
        for (row in rows.sortedBy { it.id }) {
            val fingerprint = ActivityFingerprint.of(
                row.type, row.symbol, row.description, row.quantity, row.price,
                row.amount, row.fee, row.currency, row.tradeDate, row.settlementDate, row.optionType
            )
            val key = row.connectionId to fingerprint
            if (keptByKey.containsKey(key)) {
                duplicateIds += row.id
            } else {
                keptByKey[key] = row.id
                fingerprintById[row.id] = fingerprint
            }
        }
        return BackfillPlan(fingerprintById, duplicateIds)
    }
}
