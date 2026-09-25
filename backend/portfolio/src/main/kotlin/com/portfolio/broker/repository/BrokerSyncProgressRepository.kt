package com.portfolio.broker.repository

import com.portfolio.broker.entity.BrokerSyncProgress
import com.portfolio.broker.entity.BrokerSyncProgressId
import org.springframework.data.jpa.repository.JpaRepository

interface BrokerSyncProgressRepository : JpaRepository<BrokerSyncProgress, BrokerSyncProgressId> {
    fun findByConnectionIdAndSyncKind(connectionId: Long, syncKind: String): BrokerSyncProgress?
    fun deleteByConnectionIdAndSyncKind(connectionId: Long, syncKind: String)
}
