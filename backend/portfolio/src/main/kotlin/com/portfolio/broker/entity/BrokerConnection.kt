package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.OffsetDateTime

enum class ConnectionStatus {
    PENDING, ACTIVE, EXPIRED, ERROR, DISCONNECTED
}

@Entity
@Table(name = "broker_connections")
class BrokerConnection(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_id")
    val broker: Broker? = null,

    @Column(name = "snaptrade_authorization_id", length = 255)
    var snaptradeAuthorizationId: String? = null,

    @Column(name = "gateway_connection_id", length = 36)
    var gatewayConnectionId: String? = null,

    @Column(name = "account_id_external", length = 100)
    var accountIdExternal: String? = null,

    @Column(name = "account_number", length = 50)
    var accountNumber: String? = null,

    @Column(name = "account_type", length = 50)
    var accountType: String? = null,

    @Column(name = "account_name", length = 100)
    var accountName: String? = null,

    @Column(name = "account_number_actual", length = 50)
    var accountNumberActual: String? = null,

    @Column(name = "account_meta_type", length = 50)
    var accountMetaType: String? = null,

    @Column(name = "broker_name", length = 200)
    var brokerName: String? = null,

    @Column(name = "broker_logo_url", length = 500)
    var brokerLogoUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ConnectionStatus = ConnectionStatus.PENDING,

    @Column(name = "last_positions_fetched_at")
    var lastPositionsFetchedAt: OffsetDateTime? = null,

    @Column(name = "positions_count")
    var positionsCount: Int = 0,

    @Column(name = "total_value", precision = 18, scale = 2)
    var totalValue: BigDecimal? = null,

    @Column(name = "connection_error_code", length = 50)
    var connectionErrorCode: String? = null,

    @Column(name = "connection_error_message", length = 500)
    var connectionErrorMessage: String? = null,

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var metadata: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "last_activities_fetched_at")
    var lastActivitiesFetchedAt: OffsetDateTime? = null,

    @Column(name = "last_balance_fetched_at")
    var lastBalanceFetchedAt: OffsetDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "connection_type", length = 20)
    var connectionType: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_portfolio_id")
    var modelPortfolio: ModelPortfolio? = null,

    @Column(name = "model_accuracy")
    var modelAccuracy: BigDecimal? = null,

    @Column(name = "last_rebalanced_at")
    var lastRebalancedAt: OffsetDateTime? = null,

    @OneToMany(mappedBy = "connection", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val positions: MutableList<BrokerPosition> = mutableListOf(),

    @OneToMany(mappedBy = "connection", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val fetchLogs: MutableList<PositionFetchLog> = mutableListOf()
) {
    fun isActive(): Boolean = status == ConnectionStatus.ACTIVE

    fun needsReauth(): Boolean = status == ConnectionStatus.EXPIRED || status == ConnectionStatus.ERROR

    fun markAsExpired(errorMessage: String? = null) {
        status = ConnectionStatus.EXPIRED
        connectionErrorCode = "TOKEN_EXPIRED"
        connectionErrorMessage = errorMessage ?: "Token expired, please reconnect"
    }

    fun markAsError(errorCode: String, errorMessage: String) {
        status = ConnectionStatus.ERROR
        connectionErrorCode = errorCode
        connectionErrorMessage = errorMessage
    }

    fun clearError() {
        connectionErrorCode = null
        connectionErrorMessage = null
    }
}
