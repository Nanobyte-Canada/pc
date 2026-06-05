package com.portfolio.brokergateway.adapter

import com.portfolio.brokergateway.adapter.dto.*
import java.time.LocalDate

interface BrokerAdapter {
    val brokerType: BrokerType

    fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult
    fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials
    fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount>
    fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance
    fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition>
    fun getActivities(
        credentials: BrokerCredentials,
        accountId: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): List<UnifiedActivity>
    fun getOrders(
        credentials: BrokerCredentials,
        accountId: String,
        status: OrderStatusFilter? = null
    ): List<UnifiedOrder>
    fun placeOrder(
        credentials: BrokerCredentials,
        accountId: String,
        request: OrderRequest
    ): OrderResult
    fun cancelOrder(
        credentials: BrokerCredentials,
        accountId: String,
        brokerOrderId: String
    ): CancelResult
    fun getOrderImpact(
        credentials: BrokerCredentials,
        accountId: String,
        request: OrderRequest
    ): OrderImpactResult {
        return OrderImpactResult(
            estimatedCommission = null,
            buyingPowerEffect = null,
            maintenanceExcess = null,
            isOrderAccepted = true,
            warnings = listOf("Order impact preview not supported for this broker")
        )
    }
    fun capabilities(): BrokerCapabilities
}

data class OrderStatusFilter(
    val statuses: List<OrderStatus>? = null
)
