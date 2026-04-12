package com.portfolio.broker.repository

import com.portfolio.broker.entity.PortfolioExcludedAsset
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PortfolioExcludedAssetRepository : JpaRepository<PortfolioExcludedAsset, Long> {

    fun findByGroupId(groupId: Long): List<PortfolioExcludedAsset>

    fun findByGroupIdAndSymbol(groupId: Long, symbol: String): PortfolioExcludedAsset?

    fun existsByGroupIdAndSymbol(groupId: Long, symbol: String): Boolean
}
