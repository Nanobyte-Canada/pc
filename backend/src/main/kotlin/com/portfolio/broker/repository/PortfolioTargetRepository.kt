package com.portfolio.broker.repository

import com.portfolio.broker.entity.PortfolioTarget
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PortfolioTargetRepository : JpaRepository<PortfolioTarget, Long> {

    fun findByGroupId(groupId: Long): List<PortfolioTarget>

    fun findByGroupIdAndSymbol(groupId: Long, symbol: String): PortfolioTarget?

    @Modifying
    @Query("DELETE FROM PortfolioTarget pt WHERE pt.group.id = :groupId")
    fun deleteByGroupId(groupId: Long)
}
