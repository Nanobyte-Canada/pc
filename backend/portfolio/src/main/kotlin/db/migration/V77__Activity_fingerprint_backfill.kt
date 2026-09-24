package db.migration

import com.portfolio.broker.service.ActivityBackfillPlanner
import com.portfolio.broker.service.ActivityRowForBackfill
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection

/**
 * One-time repair for broker_activities rows created before the fingerprint dedup key:
 * backfills external_id for rows without a broker id and removes duplicates, keeping the
 * earliest row per (connection, fingerprint). Only rows with NULL external_id are touched.
 */
class V77__Activity_fingerprint_backfill : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val connection = context.connection
        val rows = loadRows(connection)
        if (rows.isEmpty()) return

        val plan = ActivityBackfillPlanner.plan(rows)

        connection.prepareStatement("UPDATE broker_activities SET external_id = ? WHERE id = ?").use { ps ->
            for ((id, fingerprint) in plan.fingerprintById) {
                ps.setString(1, fingerprint)
                ps.setLong(2, id)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        connection.prepareStatement("DELETE FROM broker_activities WHERE id = ?").use { ps ->
            for (id in plan.duplicateIdsToDelete) {
                ps.setLong(1, id)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun loadRows(connection: Connection): List<ActivityRowForBackfill> {
        val rows = mutableListOf<ActivityRowForBackfill>()
        connection.prepareStatement(
            """
            SELECT id, connection_id, type, symbol, description, quantity, price, amount, fee,
                   currency, trade_date, settlement_date, option_type
            FROM broker_activities
            WHERE external_id IS NULL
            ORDER BY id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += ActivityRowForBackfill(
                        id = rs.getLong("id"),
                        connectionId = rs.getLong("connection_id"),
                        type = rs.getString("type"),
                        symbol = rs.getString("symbol"),
                        description = rs.getString("description"),
                        quantity = rs.getBigDecimal("quantity"),
                        price = rs.getBigDecimal("price"),
                        amount = rs.getBigDecimal("amount"),
                        fee = rs.getBigDecimal("fee"),
                        currency = rs.getString("currency") ?: "CAD",
                        tradeDate = rs.getDate("trade_date").toLocalDate(),
                        settlementDate = rs.getDate("settlement_date")?.toLocalDate(),
                        optionType = rs.getString("option_type")
                    )
                }
            }
        }
        return rows
    }
}
