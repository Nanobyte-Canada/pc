package com.portfolio.broker.repository

import com.portfolio.broker.entity.FetchStatus
import com.portfolio.broker.entity.PositionFetchType
import com.portfolio.broker.entity.PositionFetchLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface PositionFetchLogRepository : JpaRepository<PositionFetchLog, Long> {

    fun findByConnectionId(connectionId: Long): List<PositionFetchLog>

    fun findByConnectionIdOrderByStartedAtDesc(connectionId: Long, pageable: Pageable): Page<PositionFetchLog>

    fun findByUserId(userId: Long): List<PositionFetchLog>

    fun findByUserIdOrderByStartedAtDesc(userId: Long, pageable: Pageable): Page<PositionFetchLog>

    fun findByConnectionIdAndStatus(connectionId: Long, status: FetchStatus): List<PositionFetchLog>

    @Query("""
        SELECT pfl FROM PositionFetchLog pfl
        WHERE pfl.connection.id = :connectionId
        ORDER BY pfl.startedAt DESC
        LIMIT 1
    """)
    fun findLatestByConnectionId(connectionId: Long): PositionFetchLog?

    @Query("""
        SELECT pfl FROM PositionFetchLog pfl
        WHERE pfl.user.id = :userId
        AND pfl.status = 'FAILED'
        ORDER BY pfl.startedAt DESC
    """)
    fun findRecentFailuresByUserId(userId: Long, pageable: Pageable): Page<PositionFetchLog>

    @Query("""
        SELECT COUNT(pfl) FROM PositionFetchLog pfl
        WHERE pfl.connection.id = :connectionId
        AND pfl.status = 'FAILED'
        AND pfl.startedAt > :since
    """)
    fun countRecentFailuresByConnectionId(connectionId: Long, since: OffsetDateTime): Long

    @Query("""
        SELECT pfl FROM PositionFetchLog pfl
        WHERE pfl.fetchType = :fetchType
        AND pfl.startedAt > :since
        ORDER BY pfl.startedAt DESC
    """)
    fun findByFetchTypeSince(fetchType: PositionFetchType, since: OffsetDateTime): List<PositionFetchLog>

    @Query("""
        SELECT pfl.status, COUNT(pfl)
        FROM PositionFetchLog pfl
        WHERE pfl.startedAt > :since
        GROUP BY pfl.status
    """)
    fun countByStatusSince(since: OffsetDateTime): List<Array<Any>>

    @Query("""
        SELECT AVG(pfl.durationMs)
        FROM PositionFetchLog pfl
        WHERE pfl.status = 'SUCCESS'
        AND pfl.durationMs IS NOT NULL
        AND pfl.startedAt > :since
    """)
    fun averageDurationMsSince(since: OffsetDateTime): Double?
}
