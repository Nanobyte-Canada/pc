package com.portfolio.broker.repository

import com.portfolio.broker.entity.BrokerConnection
import com.portfolio.broker.entity.ConnectionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BrokerConnectionRepository : JpaRepository<BrokerConnection, Long> {

    fun findByUserId(userId: Long): List<BrokerConnection>

    fun findByUserIdAndStatus(userId: Long, status: ConnectionStatus): List<BrokerConnection>

    @Query("""
        SELECT bc FROM BrokerConnection bc
        JOIN FETCH bc.broker
        WHERE bc.user.id = :userId
        ORDER BY bc.broker.name, bc.accountName
    """)
    fun findByUserIdWithBroker(userId: Long): List<BrokerConnection>

    @Query("""
        SELECT bc FROM BrokerConnection bc
        JOIN FETCH bc.broker
        WHERE bc.user.id = :userId AND bc.status = :status
        ORDER BY bc.broker.name, bc.accountName
    """)
    fun findByUserIdAndStatusWithBroker(userId: Long, status: ConnectionStatus): List<BrokerConnection>

    fun findByIdAndUserId(id: Long, userId: Long): BrokerConnection?

    @Query("""
        SELECT bc FROM BrokerConnection bc
        JOIN FETCH bc.broker
        LEFT JOIN FETCH bc.token
        WHERE bc.id = :id AND bc.user.id = :userId
    """)
    fun findByIdAndUserIdWithBrokerAndToken(id: Long, userId: Long): BrokerConnection?

    fun findByUserIdAndBrokerIdAndAccountIdExternal(
        userId: Long,
        brokerId: Long,
        accountIdExternal: String
    ): BrokerConnection?

    fun existsByUserIdAndBrokerId(userId: Long, brokerId: Long): Boolean

    @Query("""
        SELECT bc FROM BrokerConnection bc
        JOIN FETCH bc.broker
        WHERE bc.user.id IN :userIds AND bc.status = 'ACTIVE'
    """)
    fun findActiveConnectionsForUsers(userIds: List<Long>): List<BrokerConnection>

    @Modifying
    @Query("UPDATE BrokerConnection bc SET bc.status = :status WHERE bc.id = :id")
    fun updateStatus(id: Long, status: ConnectionStatus)

    @Query("SELECT COUNT(bc) FROM BrokerConnection bc WHERE bc.user.id = :userId AND bc.status = 'ACTIVE'")
    fun countActiveConnectionsByUserId(userId: Long): Long

    @Query("""
        SELECT bc FROM BrokerConnection bc
        JOIN FETCH bc.broker
        WHERE bc.status = 'ACTIVE'
        AND bc.user.id IN (
            SELECT ubp.user.id FROM UserBrokerPrefs ubp
            WHERE ubp.autoFetchEnabled = true
            AND EXTRACT(HOUR FROM ubp.fetchTimeUtc) = :hour
        )
    """)
    fun findConnectionsForScheduledFetch(hour: Int): List<BrokerConnection>
}
