package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import jakarta.persistence.FetchType.LAZY
import java.time.LocalTime
import java.time.OffsetDateTime

@Entity
@Table(name = "user_broker_prefs")
class UserBrokerPrefs(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(name = "auto_fetch_enabled", nullable = false)
    var autoFetchEnabled: Boolean = false,

    @Column(name = "fetch_time_utc")
    var fetchTimeUtc: LocalTime = LocalTime.of(6, 0),

    @Column(name = "notification_on_fetch", nullable = false)
    var notificationOnFetch: Boolean = false,

    @Column(name = "notification_on_error", nullable = false)
    var notificationOnError: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
