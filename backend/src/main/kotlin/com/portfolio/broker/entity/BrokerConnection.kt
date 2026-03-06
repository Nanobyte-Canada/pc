package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
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

    @Column(name = "account_id_external", length = 100)
    var accountIdExternal: String? = null,

    @Column(name = "account_number", length = 50)
    var accountNumber: String? = null,

    @Column(name = "account_type", length = 50)
    var accountType: String? = null,

    @Column(name = "account_name", length = 100)
    var accountName: String? = null,

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
    var metadata: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

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
