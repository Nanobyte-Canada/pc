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

    @Query("""
        SELECT a FROM BrokerActivity a
        WHERE a.connection.id = :connectionId
        AND (:startDate IS NULL OR a.tradeDate >= :startDate)
        AND (:endDate IS NULL OR a.tradeDate <= :endDate)
        AND (:type IS NULL OR a.type = :type)
        ORDER BY a.tradeDate DESC
    """)
    fun findFiltered(
        connectionId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        type: String?,
        pageable: Pageable
    ): Page<BrokerActivity>

    @Query("""
        SELECT a FROM BrokerActivity a
        WHERE a.connection.id IN :connectionIds
        AND (:startDate IS NULL OR a.tradeDate >= :startDate)
        AND (:endDate IS NULL OR a.tradeDate <= :endDate)
        AND (:type IS NULL OR a.type = :type)
        ORDER BY a.tradeDate DESC
    """)
    fun findFilteredMultiConnection(
        connectionIds: List<Long>,
        startDate: LocalDate?,
        endDate: LocalDate?,
        type: String?,
        pageable: Pageable
    ): Page<BrokerActivity>
}
