package com.portfolio.broker.repository

import com.portfolio.broker.entity.BrokerOAuthState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface BrokerOAuthStateRepository : JpaRepository<BrokerOAuthState, Long> {

    fun findByStateHash(stateHash: String): BrokerOAuthState?

    @Query("""
        SELECT bos FROM BrokerOAuthState bos
        JOIN FETCH bos.user
        JOIN FETCH bos.broker
        WHERE bos.stateHash = :stateHash
    """)
    fun findByStateHashWithUserAndBroker(stateHash: String): BrokerOAuthState?

    fun existsByStateHash(stateHash: String): Boolean

    @Modifying
    @Query("DELETE FROM BrokerOAuthState bos WHERE bos.expiresAt < :now")
    fun deleteExpiredStates(now: OffsetDateTime): Int

    @Modifying
    @Query("DELETE FROM BrokerOAuthState bos WHERE bos.usedAt IS NOT NULL AND bos.usedAt < :cutoff")
    fun deleteUsedStatesBefore(cutoff: OffsetDateTime): Int

    fun findByUserIdAndBrokerId(userId: Long, brokerId: Long): List<BrokerOAuthState>

    @Modifying
    @Query("DELETE FROM BrokerOAuthState bos WHERE bos.user.id = :userId AND bos.broker.id = :brokerId")
    fun deleteByUserIdAndBrokerId(userId: Long, brokerId: Long)
}
