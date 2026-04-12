package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.Notification
import com.portfolio.broker.entity.NotificationPreference
import com.portfolio.broker.entity.NotificationType
import com.portfolio.broker.repository.NotificationPreferenceRepository
import com.portfolio.broker.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val preferenceRepository: NotificationPreferenceRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createNotification(
        user: User,
        type: NotificationType,
        title: String,
        message: String,
        link: String? = null,
        metadata: String? = null
    ): NotificationDto {
        val notification = Notification(
            user = user,
            type = type,
            title = title,
            message = message,
            link = link,
            metadata = metadata
        )
        val saved = notificationRepository.save(notification)
        log.info("Created notification {} for user {}: {}", saved.id, user.id, title)
        return saved.toDto()
    }

    fun getNotifications(userId: Long, unreadOnly: Boolean = false, page: Int = 0, size: Int = 20): NotificationsResponse {
        val pageable = PageRequest.of(page, size)
        val notificationsPage = if (unreadOnly) {
            notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
        } else {
            notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        }

        val unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId)

        return NotificationsResponse(
            notifications = notificationsPage.content.map { it.toDto() },
            unreadCount = unreadCount,
            totalCount = notificationsPage.totalElements
        )
    }

    fun getUnreadCount(userId: Long): Long {
        return notificationRepository.countByUserIdAndIsReadFalse(userId)
    }

    @Transactional
    fun markAsRead(userId: Long, notificationId: Long): NotificationDto {
        val notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            ?: throw IllegalArgumentException("Notification not found: $notificationId")
        notification.isRead = true
        notificationRepository.save(notification)
        return notification.toDto()
    }

    @Transactional
    fun markAllAsRead(userId: Long): Int {
        val count = notificationRepository.markAllAsReadByUserId(userId)
        log.info("Marked {} notifications as read for user {}", count, userId)
        return count
    }

    fun getPreferences(userId: Long): NotificationPreferenceDto {
        val pref = preferenceRepository.findByUserId(userId)
        return pref?.toDto() ?: DEFAULT_NOTIFICATION_PREFERENCE_DTO
    }

    @Transactional
    fun updatePreferences(user: User, request: UpdateNotificationPreferenceRequest): NotificationPreferenceDto {
        val pref = preferenceRepository.findByUserId(user.id)
            ?: NotificationPreference(user = user)

        request.emailEnabled?.let { pref.emailEnabled = it }
        request.inAppEnabled?.let { pref.inAppEnabled = it }
        request.driftAlerts?.let { pref.driftAlerts = it }
        request.driftThreshold?.let { pref.driftThreshold = it }
        request.orderAlerts?.let { pref.orderAlerts = it }
        request.syncFailureAlerts?.let { pref.syncFailureAlerts = it }
        request.newAssetAlerts?.let { pref.newAssetAlerts = it }
        request.rebalanceReminder?.let { pref.rebalanceReminder = it }
        request.reminderFrequency?.let { pref.reminderFrequency = it }

        preferenceRepository.save(pref)
        log.info("Updated notification preferences for user {}", user.id)
        return pref.toDto()
    }
}
