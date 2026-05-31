package com.portfolio.broker.repository

import com.portfolio.broker.entity.BrokerPosition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface BrokerPositionRepository : JpaRepository<BrokerPosition, Long> {

    fun findByConnectionId(connectionId: Long): List<BrokerPosition>

    fun findByConnectionIdAndIsCurrent(connectionId: Long, isCurrent: Boolean): List<BrokerPosition>

    @Query("""
        SELECT bp FROM BrokerPosition bp
        JOIN FETCH bp.connection bc
        WHERE bc.user.id = :userId AND bp.isCurrent = true
        ORDER BY bp.symbol
    """)
    fun findCurrentPositionsByUserId(userId: Long): List<BrokerPosition>

    @Query("""
        SELECT bp FROM BrokerPosition bp
        JOIN FETCH bp.connection bc
        WHERE bc.user.id = :userId
        AND bp.isCurrent = true
        AND bc.status = 'ACTIVE'
        ORDER BY bp.currentValue DESC
    """)
    fun findCurrentPositionsByUserIdFromActiveConnections(userId: Long): List<BrokerPosition>

    @Query("""
        SELECT bp FROM BrokerPosition bp
        WHERE bp.connection.id = :connectionId AND bp.isCurrent = true
        ORDER BY bp.currentValue DESC
    """)
    fun findCurrentPositionsByConnectionId(connectionId: Long): List<BrokerPosition>

    @Modifying
    @Query("UPDATE BrokerPosition bp SET bp.isCurrent = false WHERE bp.connection.id = :connectionId AND bp.isCurrent = true")
    fun markAllNonCurrent(connectionId: Long)

    @Modifying
    @Query("DELETE FROM BrokerPosition bp WHERE bp.connection.id = :connectionId")
    fun deleteByConnectionId(connectionId: Long)

    fun findByConnectionIdAndAsOfDate(connectionId: Long, asOfDate: LocalDate): List<BrokerPosition>

    @Query("""
        SELECT DISTINCT bp.asOfDate FROM BrokerPosition bp
        WHERE bp.connection.id = :connectionId
        ORDER BY bp.asOfDate DESC
    """)
    fun findDistinctAsOfDatesByConnectionId(connectionId: Long): List<LocalDate>

    @Query("SELECT SUM(bp.currentValue) FROM BrokerPosition bp WHERE bp.connection.id = :connectionId AND bp.isCurrent = true")
    fun sumCurrentValueByConnectionId(connectionId: Long): java.math.BigDecimal?

    @Query("SELECT COUNT(bp) FROM BrokerPosition bp WHERE bp.connection.id = :connectionId AND bp.isCurrent = true")
    fun countCurrentPositionsByConnectionId(connectionId: Long): Long

    @Query("""
        SELECT bp.symbol, SUM(bp.quantity) as totalQty, SUM(bp.currentValue) as totalValue
        FROM BrokerPosition bp
        JOIN bp.connection bc
        WHERE bc.user.id = :userId AND bp.isCurrent = true AND bc.status = 'ACTIVE'
        GROUP BY bp.symbol
        ORDER BY totalValue DESC
    """)
    fun findAggregatedPositionsByUserId(userId: Long): List<Array<Any>>

    fun findFirstBySymbolIgnoreCaseAndIsCurrentTrue(symbol: String): BrokerPosition?
}
