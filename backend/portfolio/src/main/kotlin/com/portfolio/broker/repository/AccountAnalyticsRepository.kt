package com.portfolio.broker.repository

import com.portfolio.broker.entity.AccountAnalytics
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountAnalyticsRepository : JpaRepository<AccountAnalytics, Long> {

    fun findByConnectionId(connectionId: Long): AccountAnalytics?

    fun findAllByUserId(userId: Long): List<AccountAnalytics>

    fun deleteByConnectionId(connectionId: Long)
}
