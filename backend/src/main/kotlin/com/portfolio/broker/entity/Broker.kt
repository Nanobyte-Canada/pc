package com.portfolio.broker.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

enum class BrokerAuthType {
    OAUTH2, API_KEY, AGGREGATOR
}

enum class BrokerStatus {
    ACTIVE, INACTIVE, MAINTENANCE
}

@Entity
@Table(name = "brokers")
class Broker(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code", nullable = false, unique = true, length = 20)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    val authType: BrokerAuthType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: BrokerStatus = BrokerStatus.ACTIVE,

    @Column(name = "logo_url", length = 500)
    val logoUrl: String? = null,

    @Column(name = "description", length = 500)
    val description: String? = null,

    @Column(name = "oauth_config", columnDefinition = "jsonb")
    val oauthConfig: String? = null,

    @Column(name = "rate_limit_config", columnDefinition = "jsonb")
    val rateLimitConfig: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
