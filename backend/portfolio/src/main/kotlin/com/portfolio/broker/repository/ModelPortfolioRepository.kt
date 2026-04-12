package com.portfolio.broker.repository

import com.portfolio.broker.entity.ModelPortfolio
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ModelPortfolioRepository : JpaRepository<ModelPortfolio, Long> {

    @Query("""
        SELECT m FROM ModelPortfolio m
        WHERE m.isSystem = true OR m.user.id = :userId
        ORDER BY m.isSystem DESC, m.name
    """)
    fun findAllAccessible(userId: Long): List<ModelPortfolio>

    fun findByIdAndUserId(id: Long, userId: Long): ModelPortfolio?

    fun existsByUserIdAndName(userId: Long, name: String): Boolean
}
