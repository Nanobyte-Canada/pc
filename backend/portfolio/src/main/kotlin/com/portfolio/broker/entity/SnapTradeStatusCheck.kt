package com.portfolio.broker.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

enum class SnapTradeApiStatus { ONLINE, DEGRADED, OFFLINE, UNKNOWN }

@Entity
@Table(name = "snaptrade_status_checks")
class SnapTradeStatusCheck(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: SnapTradeApiStatus,

    @Column(name = "response_time_ms")
    val responseTimeMs: Int? = null,

    val version: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "raw_response", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val rawResponse: String? = null,

    @Column(name = "checked_at", nullable = false)
    val checkedAt: OffsetDateTime = OffsetDateTime.now()
)
