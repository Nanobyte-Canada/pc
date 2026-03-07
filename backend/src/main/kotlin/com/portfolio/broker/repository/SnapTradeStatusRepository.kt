package com.portfolio.broker.repository

import com.portfolio.broker.entity.SnapTradeApiStatus
import com.portfolio.broker.entity.SnapTradeStatusCheck
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface SnapTradeStatusRepository : JpaRepository<SnapTradeStatusCheck, Long> {

    @Query("SELECT s FROM SnapTradeStatusCheck s ORDER BY s.checkedAt DESC LIMIT 1")
    fun findLatest(): SnapTradeStatusCheck?

    @Query("SELECT s FROM SnapTradeStatusCheck s WHERE s.checkedAt >= :since ORDER BY s.checkedAt DESC")
    fun findRecentChecks(@Param("since") since: OffsetDateTime): List<SnapTradeStatusCheck>

    @Query("SELECT COUNT(s) FROM SnapTradeStatusCheck s WHERE s.status = :status AND s.checkedAt >= :since")
    fun countByStatusSince(@Param("status") status: SnapTradeApiStatus, @Param("since") since: OffsetDateTime): Long

    @Query("SELECT COUNT(s) FROM SnapTradeStatusCheck s WHERE s.checkedAt >= :since")
    fun countChecksSince(@Param("since") since: OffsetDateTime): Long
}
