package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "notification_preferences")
class NotificationPreference(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "email_enabled", nullable = false)
    var emailEnabled: Boolean = true,

    @Column(name = "in_app_enabled", nullable = false)
    var inAppEnabled: Boolean = true,

    @Column(name = "drift_alerts", nullable = false)
    var driftAlerts: Boolean = true,

    @Column(name = "drift_threshold", nullable = false, precision = 5, scale = 2)
    var driftThreshold: BigDecimal = BigDecimal("90.00"),

    @Column(name = "order_alerts", nullable = false)
    var orderAlerts: Boolean = true,

    @Column(name = "sync_failure_alerts", nullable = false)
    var syncFailureAlerts: Boolean = true,

    @Column(name = "new_asset_alerts", nullable = false)
    var newAssetAlerts: Boolean = true,

    @Column(name = "rebalance_reminder", nullable = false)
    var rebalanceReminder: Boolean = false,

    @Column(name = "reminder_frequency", length = 20)
    var reminderFrequency: String = "WEEKLY",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
