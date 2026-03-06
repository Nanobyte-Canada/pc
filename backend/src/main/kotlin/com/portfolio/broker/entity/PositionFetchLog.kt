package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import jakarta.persistence.FetchType as JpaFetchType
import java.math.BigDecimal
import java.time.OffsetDateTime

enum class PositionFetchType {
    MANUAL, INITIAL
}

enum class FetchStatus {
    PENDING, IN_PROGRESS, SUCCESS, FAILED, PARTIAL, CANCELLED
}

@Entity
@Table(name = "position_fetch_log")
class PositionFetchLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = JpaFetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    val connection: BrokerConnection,

    @ManyToOne(fetch = JpaFetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_type", nullable = false, length = 20)
    val fetchType: PositionFetchType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: FetchStatus = FetchStatus.PENDING,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Column(name = "duration_ms")
    var durationMs: Int? = null,

    @Column(name = "positions_count")
    var positionsCount: Int? = null,

    @Column(name = "total_value", precision = 18, scale = 2)
    var totalValue: BigDecimal? = null,

    @Column(name = "error_code", length = 50)
    var errorCode: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "raw_response", columnDefinition = "jsonb")
    var rawResponse: String? = null,

    @Column(name = "retry_count")
    var retryCount: Int = 0,

    @Column(name = "triggered_by", length = 50)
    val triggeredBy: String? = null
) {
    fun markSuccess(positionsCount: Int, totalValue: BigDecimal?) {
        this.status = FetchStatus.SUCCESS
        this.completedAt = OffsetDateTime.now()
        this.durationMs = ((completedAt!!.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli())).toInt()
        this.positionsCount = positionsCount
        this.totalValue = totalValue
    }

    fun markFailed(errorCode: String, errorMessage: String) {
        this.status = FetchStatus.FAILED
        this.completedAt = OffsetDateTime.now()
        this.durationMs = ((completedAt!!.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli())).toInt()
        this.errorCode = errorCode
        this.errorMessage = errorMessage
    }

    fun markPartial(positionsCount: Int, errorMessage: String) {
        this.status = FetchStatus.PARTIAL
        this.completedAt = OffsetDateTime.now()
        this.durationMs = ((completedAt!!.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli())).toInt()
        this.positionsCount = positionsCount
        this.errorMessage = errorMessage
    }
}
