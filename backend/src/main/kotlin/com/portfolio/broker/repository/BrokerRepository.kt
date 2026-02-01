package com.portfolio.broker.repository

import com.portfolio.broker.entity.Broker
import com.portfolio.broker.entity.BrokerStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BrokerRepository : JpaRepository<Broker, Long> {

    fun findByCode(code: String): Broker?

    fun findByStatus(status: BrokerStatus): List<Broker>

    fun findByStatusOrderByNameAsc(status: BrokerStatus): List<Broker>

    fun existsByCode(code: String): Boolean
}
