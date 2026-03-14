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

    fun findByStatus(status: ConnectionStatus): List<BrokerConnection>

    fun findByUserIdAndStatus(userId: Long, status: ConnectionStatus): List<BrokerConnection>

    @Query("""
        SELECT bc FROM BrokerConnection bc
        LEFT JOIN FETCH bc.broker
        WHERE bc.user.id = :userId
        ORDER BY bc.accountName
    """)
    fun findByUserIdWithBroker(userId: Long): List<BrokerConnection>

    @Query("""
        SELECT bc FROM BrokerConnection bc
        LEFT JOIN FETCH bc.broker
        WHERE bc.user.id = :userId AND bc.status = :status
        ORDER BY bc.accountName
    """)
    fun findByUserIdAndStatusWithBroker(userId: Long, status: ConnectionStatus): List<BrokerConnection>

    fun findByIdAndUserId(id: Long, userId: Long): BrokerConnection?

    @Query("""
        SELECT bc FROM BrokerConnection bc
        LEFT JOIN FETCH bc.broker
        WHERE bc.id = :id AND bc.user.id = :userId
    """)
    fun findByIdAndUserIdWithBroker(id: Long, userId: Long): BrokerConnection?

    fun findBySnaptradeAuthorizationId(snaptradeAuthorizationId: String): BrokerConnection?

    fun findByUserIdAndSnaptradeAuthorizationId(userId: Long, snaptradeAuthorizationId: String): BrokerConnection?

    fun findByUserIdAndAccountIdExternal(userId: Long, accountIdExternal: String): BrokerConnection?

    fun existsByUserIdAndSnaptradeAuthorizationId(userId: Long, snaptradeAuthorizationId: String): Boolean

    @Query("""
        SELECT bc FROM BrokerConnection bc
        LEFT JOIN FETCH bc.broker
        WHERE bc.user.id IN :userIds AND bc.status = 'ACTIVE'
    """)
    fun findActiveConnectionsForUsers(userIds: List<Long>): List<BrokerConnection>

    @Modifying
    @Query("UPDATE BrokerConnection bc SET bc.status = :status WHERE bc.id = :id")
    fun updateStatus(id: Long, status: ConnectionStatus)

    @Query("SELECT COUNT(bc) FROM BrokerConnection bc WHERE bc.user.id = :userId AND bc.status = 'ACTIVE'")
    fun countActiveConnectionsByUserId(userId: Long): Long
}
