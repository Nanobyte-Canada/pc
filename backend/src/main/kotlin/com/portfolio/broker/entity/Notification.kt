package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import java.time.OffsetDateTime

enum class NotificationType {
    DRIFT_ALERT,
    ORDER_FILLED,
    ORDER_REJECTED,
    ORDER_FAILED,
    SYNC_FAILURE,
    NEW_ASSET,
    REBALANCE_REMINDER,
    SYSTEM
}

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    val type: NotificationType,

    @Column(name = "title", nullable = false, length = 200)
    val title: String,

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    val message: String,

    @Column(name = "link", length = 500)
    val link: String? = null,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "metadata", columnDefinition = "JSONB")
    val metadata: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
