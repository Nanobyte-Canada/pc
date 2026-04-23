package com.portfolio.brokergateway.credential

import org.springframework.data.jpa.repository.JpaRepository

interface GatewayConnectionRepository : JpaRepository<GatewayConnection, String> {
    fun findByUserId(userId: Long): List<GatewayConnection>
    fun findByUserIdAndBrokerType(userId: Long, brokerType: String): List<GatewayConnection>
    fun findByStatus(status: String): List<GatewayConnection>
}
