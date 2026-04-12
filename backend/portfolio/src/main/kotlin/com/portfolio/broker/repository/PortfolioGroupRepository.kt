package com.portfolio.broker.repository

import com.portfolio.broker.entity.PortfolioGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PortfolioGroupRepository : JpaRepository<PortfolioGroup, Long> {

    fun findByUserId(userId: Long): List<PortfolioGroup>

    fun findByIdAndUserId(id: Long, userId: Long): PortfolioGroup?

    fun existsByUserIdAndName(userId: Long, name: String): Boolean

    @Query("""
        SELECT pg FROM PortfolioGroup pg
        LEFT JOIN FETCH pg.targets
        LEFT JOIN FETCH pg.linkedAccounts
        LEFT JOIN FETCH pg.settings
        LEFT JOIN FETCH pg.excludedAssets
        WHERE pg.id = :id AND pg.user.id = :userId
    """)
    fun findByIdAndUserIdWithDetails(id: Long, userId: Long): PortfolioGroup?
}
