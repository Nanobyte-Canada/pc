package com.portfolio.broker.repository

import com.portfolio.broker.entity.ModelPortfolioAllocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ModelPortfolioAllocationRepository : JpaRepository<ModelPortfolioAllocation, Long> {

    fun findByModelPortfolioId(modelPortfolioId: Long): List<ModelPortfolioAllocation>

    fun deleteByModelPortfolioId(modelPortfolioId: Long)
}
