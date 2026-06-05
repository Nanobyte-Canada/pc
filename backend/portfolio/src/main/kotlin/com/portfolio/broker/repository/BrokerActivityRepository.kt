package com.portfolio.broker.repository

import com.portfolio.broker.entity.BrokerActivity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface BrokerActivityRepository : JpaRepository<BrokerActivity, Long> {

    fun findByConnectionIdOrderByTradeDateDesc(connectionId: Long, pageable: Pageable): Page<BrokerActivity>

    fun findByConnectionIdAndTradeDateBetween(
        connectionId: Long, startDate: LocalDate, endDate: LocalDate
    ): List<BrokerActivity>

    fun findByConnectionIdInAndTradeDateBetween(
        connectionIds: List<Long>, startDate: LocalDate, endDate: LocalDate
    ): List<BrokerActivity>

    fun findByConnectionIdAndExternalId(connectionId: Long, externalId: String): BrokerActivity?

    @Query("SELECT MAX(a.tradeDate) FROM BrokerActivity a WHERE a.connection.id = :connectionId")
    fun findLatestTradeDateByConnectionId(connectionId: Long): LocalDate?

    @Query("SELECT MIN(a.tradeDate) FROM BrokerActivity a WHERE a.connection.id = :connectionId")
    fun findEarliestTradeDateByConnectionId(connectionId: Long): LocalDate?

    @Query(
        value = """
            SELECT * FROM broker_activities a
            WHERE a.connection_id = :connectionId
            AND (CAST(:startDate AS date) IS NULL OR a.trade_date >= CAST(:startDate AS date))
            AND (CAST(:endDate AS date) IS NULL OR a.trade_date <= CAST(:endDate AS date))
            AND (CAST(:type AS varchar) IS NULL OR a.type = CAST(:type AS varchar))
            ORDER BY a.trade_date DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM broker_activities a
            WHERE a.connection_id = :connectionId
            AND (CAST(:startDate AS date) IS NULL OR a.trade_date >= CAST(:startDate AS date))
            AND (CAST(:endDate AS date) IS NULL OR a.trade_date <= CAST(:endDate AS date))
            AND (CAST(:type AS varchar) IS NULL OR a.type = CAST(:type AS varchar))
        """,
        nativeQuery = true
    )
    fun findFiltered(
        connectionId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        type: String?,
        pageable: Pageable
    ): Page<BrokerActivity>

    @Query(
        value = """
            SELECT * FROM broker_activities a
            WHERE a.connection_id IN (:connectionIds)
            AND (CAST(:startDate AS date) IS NULL OR a.trade_date >= CAST(:startDate AS date))
            AND (CAST(:endDate AS date) IS NULL OR a.trade_date <= CAST(:endDate AS date))
            AND (CAST(:type AS varchar) IS NULL OR a.type = CAST(:type AS varchar))
            ORDER BY a.trade_date DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM broker_activities a
            WHERE a.connection_id IN (:connectionIds)
            AND (CAST(:startDate AS date) IS NULL OR a.trade_date >= CAST(:startDate AS date))
            AND (CAST(:endDate AS date) IS NULL OR a.trade_date <= CAST(:endDate AS date))
            AND (CAST(:type AS varchar) IS NULL OR a.type = CAST(:type AS varchar))
        """,
        nativeQuery = true
    )
    fun findFilteredMultiConnection(
        connectionIds: List<Long>,
        startDate: LocalDate?,
        endDate: LocalDate?,
        type: String?,
        pageable: Pageable
    ): Page<BrokerActivity>
}
