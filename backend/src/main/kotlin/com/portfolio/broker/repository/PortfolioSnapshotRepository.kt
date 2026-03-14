package com.portfolio.broker.repository

import com.portfolio.broker.entity.PortfolioSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface PortfolioSnapshotRepository : JpaRepository<PortfolioSnapshot, Long> {

    fun findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
        groupId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PortfolioSnapshot>

    fun existsByGroupIdAndSnapshotDate(groupId: Long, snapshotDate: LocalDate): Boolean
}
