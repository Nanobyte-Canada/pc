package com.portfolio.broker.repository

import com.portfolio.broker.entity.PortfolioGroupAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PortfolioGroupAccountRepository : JpaRepository<PortfolioGroupAccount, Long> {

    fun findByGroupId(groupId: Long): List<PortfolioGroupAccount>

    fun findByConnectionId(connectionId: Long): List<PortfolioGroupAccount>

    fun findByGroupIdAndConnectionId(groupId: Long, connectionId: Long): PortfolioGroupAccount?

    fun existsByGroupIdAndConnectionId(groupId: Long, connectionId: Long): Boolean
}
