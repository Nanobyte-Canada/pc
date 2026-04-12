package com.portfolio.broker.dto

import com.portfolio.broker.entity.Notification
import com.portfolio.broker.entity.NotificationPreference
import java.math.BigDecimal
import java.time.OffsetDateTime

// ========== Request DTOs ==========

data class UpdateNotificationPreferenceRequest(
    val emailEnabled: Boolean? = null,
    val inAppEnabled: Boolean? = null,
    val driftAlerts: Boolean? = null,
    val driftThreshold: BigDecimal? = null,
    val orderAlerts: Boolean? = null,
    val syncFailureAlerts: Boolean? = null,
    val newAssetAlerts: Boolean? = null,
    val rebalanceReminder: Boolean? = null,
    val reminderFrequency: String? = null
)

// ========== Response DTOs ==========

data class NotificationDto(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val link: String?,
    val isRead: Boolean,
    val metadata: String?,
    val createdAt: OffsetDateTime
)

data class NotificationsResponse(
    val notifications: List<NotificationDto>,
    val unreadCount: Long,
    val totalCount: Long
)

data class NotificationPreferenceDto(
    val emailEnabled: Boolean,
    val inAppEnabled: Boolean,
    val driftAlerts: Boolean,
    val driftThreshold: BigDecimal,
    val orderAlerts: Boolean,
    val syncFailureAlerts: Boolean,
    val newAssetAlerts: Boolean,
    val rebalanceReminder: Boolean,
    val reminderFrequency: String
)

// ========== Mappers ==========

fun Notification.toDto() = NotificationDto(
    id = id,
    type = type.name,
    title = title,
    message = message,
    link = link,
    isRead = isRead,
    metadata = metadata,
    createdAt = createdAt
)

fun NotificationPreference.toDto() = NotificationPreferenceDto(
    emailEnabled = emailEnabled,
    inAppEnabled = inAppEnabled,
    driftAlerts = driftAlerts,
    driftThreshold = driftThreshold,
    orderAlerts = orderAlerts,
    syncFailureAlerts = syncFailureAlerts,
    newAssetAlerts = newAssetAlerts,
    rebalanceReminder = rebalanceReminder,
    reminderFrequency = reminderFrequency
)

val DEFAULT_NOTIFICATION_PREFERENCE_DTO = NotificationPreferenceDto(
    emailEnabled = true,
    inAppEnabled = true,
    driftAlerts = true,
    driftThreshold = BigDecimal("90.00"),
    orderAlerts = true,
    syncFailureAlerts = true,
    newAssetAlerts = true,
    rebalanceReminder = false,
    reminderFrequency = "WEEKLY"
)
