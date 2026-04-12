package com.portfolio.broker.repository

import com.portfolio.broker.entity.RebalanceEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RebalanceEventRepository : JpaRepository<RebalanceEvent, Long> {

    fun findByGroupIdOrderByCreatedAtDesc(groupId: Long): List<RebalanceEvent>
}
