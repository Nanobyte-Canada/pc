package com.portfolio.broker.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class RebalanceTriggerType {
    SCHEDULED, ACCURACY_DROP, MANUAL
}

enum class RebalanceStatus {
    COMPLETED, FAILED, SKIPPED, PENDING_APPROVAL
}

@Entity
@Table(name = "rebalance_events")
class RebalanceEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: PortfolioGroup,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    val triggerType: RebalanceTriggerType,

    @Column(name = "accuracy_before", precision = 5, scale = 2)
    val accuracyBefore: BigDecimal? = null,

    @Column(name = "accuracy_after", precision = 5, scale = 2)
    var accuracyAfter: BigDecimal? = null,

    @Column(name = "trades_count", nullable = false)
    var tradesCount: Int = 0,

    @Column(name = "batch_id")
    var batchId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RebalanceStatus = RebalanceStatus.COMPLETED,

    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
