package com.portfolio.broker.repository

import com.portfolio.broker.entity.PortfolioCashFlow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface PortfolioCashFlowRepository : JpaRepository<PortfolioCashFlow, Long> {

    fun findByGroupIdAndFlowDateBetweenOrderByFlowDateAsc(
        groupId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PortfolioCashFlow>
}
