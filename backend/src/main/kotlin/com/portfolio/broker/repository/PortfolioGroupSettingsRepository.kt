package com.portfolio.broker.repository

import com.portfolio.broker.entity.PortfolioGroupSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PortfolioGroupSettingsRepository : JpaRepository<PortfolioGroupSettings, Long> {

    fun findByGroupId(groupId: Long): PortfolioGroupSettings?
}
