package com.portfolio.broker.repository

import com.portfolio.broker.entity.ConnectionToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ConnectionTokenRepository : JpaRepository<ConnectionToken, Long> {

    fun findByConnectionId(connectionId: Long): ConnectionToken?

    @Query("""
        SELECT ct FROM ConnectionToken ct
        JOIN FETCH ct.connection bc
        JOIN FETCH bc.broker
        WHERE ct.connection.id = :connectionId
    """)
    fun findByConnectionIdWithConnection(connectionId: Long): ConnectionToken?

    fun existsByConnectionId(connectionId: Long): Boolean

    fun deleteByConnectionId(connectionId: Long)

    @Query("""
        SELECT ct FROM ConnectionToken ct
        JOIN FETCH ct.connection bc
        WHERE ct.expiresAt IS NOT NULL
        AND ct.expiresAt < :threshold
        AND bc.status = 'ACTIVE'
    """)
    fun findExpiringTokens(threshold: OffsetDateTime): List<ConnectionToken>

    @Query("""
        SELECT ct FROM ConnectionToken ct
        JOIN FETCH ct.connection bc
        WHERE ct.expiresAt IS NOT NULL
        AND ct.expiresAt < :now
        AND bc.status = 'ACTIVE'
    """)
    fun findExpiredTokens(now: OffsetDateTime): List<ConnectionToken>
}
