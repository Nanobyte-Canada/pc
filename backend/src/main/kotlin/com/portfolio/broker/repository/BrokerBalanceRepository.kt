package com.portfolio.broker.repository

import com.portfolio.broker.entity.BrokerBalanceSnapshot
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface BrokerBalanceRepository : JpaRepository<BrokerBalanceSnapshot, Long> {

    fun findByConnectionIdOrderByAsOfDateDesc(connectionId: Long, pageable: Pageable): Page<BrokerBalanceSnapshot>

    fun findByConnectionIdAndAsOfDateBetween(
        connectionId: Long, startDate: LocalDate, endDate: LocalDate
    ): List<BrokerBalanceSnapshot>

    fun findByConnectionIdAndAsOfDate(connectionId: Long, asOfDate: LocalDate): BrokerBalanceSnapshot?

    @Query("""
        SELECT b FROM BrokerBalanceSnapshot b
        WHERE b.connection.id = :connectionId
        ORDER BY b.asOfDate DESC
        LIMIT 1
    """)
    fun findLatestByConnectionId(connectionId: Long): BrokerBalanceSnapshot?

    fun findByConnectionIdInAndAsOfDateBetween(
        connectionIds: List<Long>, startDate: LocalDate, endDate: LocalDate
    ): List<BrokerBalanceSnapshot>
}
