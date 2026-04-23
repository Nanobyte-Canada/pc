package com.portfolio.brokergateway.credential

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "connections", schema = "broker_gateway")
class GatewayConnection(
    @Id
    @Column(length = 36)
    val id: String = java.util.UUID.randomUUID().toString(),

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "broker_type", nullable = false, length = 20)
    val brokerType: String,

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE",

    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "TEXT")
    var credentialsEncrypted: String,

    @Column(name = "accounts_json", columnDefinition = "jsonb")
    var accountsJson: String? = null,

    @Column(name = "last_validated_at")
    var lastValidatedAt: OffsetDateTime? = null,

    @Column(name = "last_refreshed_at")
    var lastRefreshedAt: OffsetDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
